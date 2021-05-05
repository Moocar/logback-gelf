package me.moocar.logbackgelf;

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
public class GelfLayoutAccess<E extends IAccessEvent> extends LayoutBase<E> {

	private final String DEFAULT_FULL_MESSAGE_PATTERN = "combined";
	private final String DEFAULT_SHORT_MESSAGE_PATTERN = "combined";

	private boolean useThreadName = false;

	private Map<String, String> staticFields = new HashMap<String, String>();
	private String host = getLocalHostName();
	private final Gson gson;
	private Layout fullMessageLayout;
	private Layout shortMessageLayout;

	public GelfLayoutAccess() {

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
	 * Add a static field. A static field is a key/value pair that should be sent in each Gelf message. This supercedes static additional
	 * fields, which can't have colon characters in their value.
	 */
	public void addStaticField(Field entry) {
		staticFields.put(entry.getKey(), entry.getValue());
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
