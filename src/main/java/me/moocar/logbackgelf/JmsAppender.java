package me.moocar.logbackgelf;

import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;

import ch.qos.logback.core.OutputStreamAppender;

public class JmsAppender<E> extends OutputStreamAppender<E> {
	private final Object sessionLock = new Object();
	private final Pattern hostPattern = Pattern.compile("\"host\"\\w?:\\w?\"(.+?)\"");
	private String connectionFactoryJndiLoc = "openejb:Resource/jms/connectionFactory";
	private String queueName = "org.logback.logs";
	private Connection connection;
	private Session session;
	private MessageProducer producer;

	@Override
	public void start() {
		synchronized (sessionLock) {
			Context ctx = null;
			try {
				ctx = new InitialContext();
				ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup(connectionFactoryJndiLoc);
				Connection connection = connectionFactory.createConnection();
				connection.start();
				session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
				Queue queue = session.createQueue(queueName);
				producer = session.createProducer(queue);
				encoder.start();
				this.started = true;
			} catch (Exception e) {
				addError("start() Error while connecting to JMS", e);
			} finally {
				try {
					ctx.close();
				} catch (Exception e) {
					addError("stop() Error closing context", e);
				}
			}
		}
	}

	@Override
	public void stop() {
		synchronized (sessionLock) {
			try {
				try {
					connection.close();
				} catch (Exception e) {
					addError("stop() Error while stopping JMS", e);
				}
			} finally {
				encoder.stop();
			}
		}
	}

	@Override
	protected void append(E eventObject) {
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			synchronized (encoder) {
				encoder.init(os);
				try {
					encoder.doEncode(eventObject);
				} finally {
					encoder.init(null);
				}
			}
			String text = new String(os.toByteArray());
			Matcher matcher = hostPattern.matcher(text);
			if (matcher.find()) {
				String hostName = matcher.group(1);
				text = text.substring(0, text.length() - 1);
				StringBuilder sb = new StringBuilder(text);
				sb.append(",\"_hostName\":\"");
				sb.append(hostName);
				sb.append("\"}");
				text = sb.toString();
			}
			synchronized (sessionLock) {
				Message message = session.createTextMessage(text);
				producer.send(message);
			}
		} catch (Exception e) {
			addError("append() Error while appending to JMS", e);
			stop();
			start();
		}
	}

	public String getConnectionFactoryJndiLoc() {
		return connectionFactoryJndiLoc;
	}

	public void setConnectionFactoryJndiLoc(String connectionFactoryJndiLoc) {
		this.connectionFactoryJndiLoc = connectionFactoryJndiLoc;
	}

	public String getQueueName() {
		return queueName;
	}

	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}
}
