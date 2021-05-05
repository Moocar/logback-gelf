package me.moocar.logbackgelf;

import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import me.moocar.logbackgelf.Field;
import me.moocar.logbackgelf.InternetUtils;
import ch.qos.logback.access.PatternLayout;
import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Responsible for formatting a log event into a GELF JSON string
 */
public class GelfLayoutAccessLog<E extends IAccessEvent> extends LayoutBase<E> {

	private final String DEFAULT_FULL_MESSAGE_PATTERN = "combined";
	private final String DEFAULT_SHORT_MESSAGE_PATTERN = "combined";

	private boolean useThreadName = false;

	private Map<String, String> additionalFields = new HashMap<String, String>();
	private Map<String, String> fieldTypes = new HashMap<String, String>();
	private Map<String, String> staticFields = new HashMap<String, String>();
	private String host = getLocalHostName();
	private final Gson gson;
	private Layout fullMessageLayout;
	private Layout shortMessageLayout;
	private boolean includeFullMDC = false;

	static Map<String, Method> primitiveTypes;

	static {
		primitiveTypes = new HashMap<String, Method>();
		try {
			primitiveTypes.put("int", Integer.class.getDeclaredMethod("parseInt", String.class));
			primitiveTypes.put("Integer", Integer.class.getDeclaredMethod("parseInt", String.class));
			primitiveTypes.put("long", Long.class.getDeclaredMethod("parseLong", String.class));
			primitiveTypes.put("Long", Long.class.getDeclaredMethod("parseLong", String.class));
			primitiveTypes.put("float", Float.class.getDeclaredMethod("parseFloat", String.class));
			primitiveTypes.put("Float", Float.class.getDeclaredMethod("parseFloat", String.class));
			primitiveTypes.put("double", Double.class.getDeclaredMethod("parseDouble", String.class));
			primitiveTypes.put("Double", Double.class.getDeclaredMethod("parseDouble", String.class));
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	public GelfLayoutAccessLog() {

		// Init GSON for underscores
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
		this.gson = gsonBuilder.create();
	}

	@Override
	public void start() {

		if (fullMessageLayout == null) {
			this.fullMessageLayout = initNewPatternLayout(DEFAULT_FULL_MESSAGE_PATTERN);
		}

		if (shortMessageLayout == null) {
			this.shortMessageLayout = initNewPatternLayout(DEFAULT_SHORT_MESSAGE_PATTERN);
		}

		super.start();
	}

	private PatternLayout initNewPatternLayout(String pattern) {
		PatternLayout layout = new PatternLayout();
		layout.setPattern(pattern);
		layout.setContext(this.getContext());
		layout.start();
		return layout;
	}

	@Override
	public String doLayout(E event) {
		return gson.toJson(mapFields(event));
	}

	/**
	 * Creates a map of properties that represent the GELF message.
	 * @param logEvent The log event
	 * @return map of gelf properties
	 */
	private Map<String, Object> mapFields(E logEvent) {
		Map<String, Object> map = new HashMap<String, Object>();

		map.put("host", host);

		map.put("full_message", fullMessageLayout.doLayout(logEvent));
		map.put("short_message", shortMessageLayout.doLayout(logEvent));

		map.put("timestamp", logEvent.getTimeStamp() / 1000.0);

		map.put("version", "1.1");

		additionalFields(map, logEvent);

		staticAdditionalFields(map);

		return map;
	}

	/**
	 * Converts the additional fields into proper GELF JSON
	 * @param map The map of additional fields
	 * @param eventObject The Logging event that we are converting to GELF
	 */
	private void additionalFields(Map<String, Object> map, IAccessEvent eventObject) {

		if (useThreadName) {
			map.put("_threadName", eventObject.getThreadName());
		}

	}

	private Object convertFieldType(Object value, final String type) {
		if (primitiveTypes.containsKey(fieldTypes.get(type))) {
			try {
				value = primitiveTypes.get(fieldTypes.get(type)).invoke(null,
						value);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		return value;
	}

	private void staticAdditionalFields(Map<String, Object> map) {

		for (String key : staticFields.keySet()) {
			map.put(key, (staticFields.get(key)));
		}
	}

	private String getLocalHostName() {
		try {
			return InternetUtils.getLocalHostName();
		} catch (SocketException e) {
			return "UNKNOWN";
		} catch (UnknownHostException e) {
			return "UNKNOWN";
		}
	}

	// ////////// Logback Property Getter/Setters ////////////////

	/**
	 * If true, an additional field call "_threadName" will be added to each gelf message. Its contents will be the Name of the thread.
	 * Defaults to "false".
	 */
	public boolean isUseThreadName() {
		return useThreadName;
	}

	public void setUseThreadName(boolean useThreadName) {
		this.useThreadName = useThreadName;
	}

	/**
	 * additional fields to add to the gelf message. Here's how these work: <br/>
	 * Let's take an example. I want to log the client's ip address of every request that comes into my web server. To do this, I add the
	 * ipaddress to the slf4j MDC on each request as follows: <code> ... MDC.put("ipAddress", "44.556.345.657"); ... </code> Now, to include
	 * the ip address in the gelf message, i just add the following to my logback.groovy: <code>
	 * appender("GELF", GelfAppender) { ... additionalFields = [identity:"_identity"] ... } </code> in the additionalFields map, the key is
	 * the name of the MDC to look up. the value is the name that should be given to the key in the additional field in the gelf message.
	 */
	public Map<String, String> getAdditionalFields() {
		return additionalFields;
	}

	public void setAdditionalFields(Map<String, String> additionalFields) {
		this.additionalFields = additionalFields;
	}

	/**
	 * static additional fields to add to every gelf message. Key is the additional field key (and should thus begin with an underscore).
	 * The value is a static string.
	 */
	public Map<String, String> getStaticFields() {
		return staticFields;
	}

	public void setStaticFields(Map<String, String> staticFields) {
		this.staticFields = staticFields;
	}

	/**
	 * Indicates if all values from the MDC should be included in the gelf message or only the once listed as {@link #getAdditionalFields()
	 * additional fields}.
	 * <p>
	 * If <code>true</code>, the gelf message will contain all values available in the MDC. Each MDC key will be converted to a gelf custom
	 * field by adding an underscore prefix. If an entry exists in {@link #getAdditionalFields() additional field} it will be used instead.
	 * </p>
	 * <p>
	 * If <code>false</code>, only the fields listed in {@link #getAdditionalFields() additional field} will be included in the message.
	 * </p>
	 * @return the includeFullMDC
	 */
	public boolean isIncludeFullMDC() {
		return includeFullMDC;
	}

	public void setIncludeFullMDC(boolean includeFullMDC) {
		this.includeFullMDC = includeFullMDC;
	}

	/**
	 * Override the local host using a config option
	 * @return the local host (defaults to getLocalHost() if not overridden in config
	 */
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Add an additional field. This is mainly here for compatibility with logback.xml
	 * @param keyValue This must be in format key:value where key is the MDC key, and value is the GELF field name. e.g
	 *            "ipAddress:_ip_address"
	 */
	public void addAdditionalField(String keyValue) {
		String[] splitted = keyValue.split(":");

		if (splitted.length != 2) {

			throw new IllegalArgumentException("additionalField must be of the format key:value, where key is the MDC "
					+ "key, and value is the GELF field name. But found '" + keyValue + "' instead.");
		}

		additionalFields.put(splitted[0], splitted[1]);
	}

	/**
	 * Add a staticAdditional field. This is mainly here for compatibility with logback.xml
	 * @param keyValue This must be in format key:value where key is the additional field key, and value is a static string. e.g
	 *            "_node_name:www013"
	 * @deprecated Use addStaticField instead
	 */
	@Deprecated
	public void addStaticAdditionalField(String keyValue) {
		String[] splitted = keyValue.split(":");

		if (splitted.length != 2) {

			throw new IllegalArgumentException("staticAdditionalField must be of the format key:value, where key is the "
					+ "additional field key (therefore should have a leading underscore), and value is a static string. " +
					"e.g. _node_name:www013");
		}

		staticFields.put(splitted[0], splitted[1]);
	}

	/**
	 * Add a static field. A static field is a key/value pair that should be sent in each Gelf message. This supercedes static additional
	 * fields, which can't have colon characters in their value.
	 */
	public void addStaticField(Field entry) {
		staticFields.put(entry.getKey(), entry.getValue());
	}

	public void addFieldType(String keyValue) {
		String[] splitted = keyValue.split(":");

		if (splitted.length != 2 ||
				!GelfLayoutAccessLog.primitiveTypes.containsKey(splitted[1])) {
			throw new IllegalArgumentException(
					"fieldType must be of the format key:value, where key is the " +
							"field key, and value is the type to convert to (one of " +
							GelfLayoutAccessLog.primitiveTypes.keySet() +
							")");
		}

		fieldTypes.put(splitted[0], splitted[1]);

	}

	public Map<String, String> getFieldTypes() {
		return fieldTypes;
	}

	public void setFieldTypes(final Map<String, String> fieldTypes) {
		this.fieldTypes = fieldTypes;
	}

	public Layout getFullMessageLayout() {
		return fullMessageLayout;
	}

	public void setFullMessageLayout(Layout fullMessageLayout) {
		this.fullMessageLayout = fullMessageLayout;
	}

	public Layout getShortMessageLayout() {
		return shortMessageLayout;
	}

	public void setShortMessageLayout(Layout shortMessageLayout) {
		this.shortMessageLayout = shortMessageLayout;
	}
}
