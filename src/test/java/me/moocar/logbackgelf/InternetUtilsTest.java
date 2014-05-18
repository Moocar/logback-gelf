package me.moocar.logbackgelf;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(InternetUtils.class)
public class InternetUtilsTest {
	private static final String RESULT_HOSTNAME = "hostname";
	private static final String RESULT_FQDN = "hostname.with.do-main";
	private static final String RESULT_IP = "1.2.3.4";

	@Mock
	private InetAddress myAddress;

	@Before
	public void setUp() throws UnknownHostException {
		PowerMockito.mockStatic(InetAddress.class);
		PowerMockito.when(InetAddress.getLocalHost()).thenReturn(myAddress);
	}

	@Test
	public void testHostFQDN() throws UnknownHostException, SocketException {
		PowerMockito.when(myAddress.getHostName()).thenReturn(RESULT_HOSTNAME);
		PowerMockito.when(myAddress.getCanonicalHostName()).thenReturn(RESULT_FQDN);
		Assert.assertEquals(RESULT_FQDN, InternetUtils.getLocalHostName());
	}

	@Test
	public void testHostIP() throws UnknownHostException, SocketException {
		PowerMockito.when(myAddress.getHostName()).thenReturn(RESULT_HOSTNAME);
		PowerMockito.when(myAddress.getCanonicalHostName()).thenReturn(RESULT_IP);
		Assert.assertEquals(RESULT_HOSTNAME, InternetUtils.getLocalHostName());
	}

	@Test
	public void testFQDNIP() throws UnknownHostException, SocketException {
		PowerMockito.when(myAddress.getHostName()).thenReturn(RESULT_FQDN);
		PowerMockito.when(myAddress.getCanonicalHostName()).thenReturn(RESULT_IP);
		Assert.assertEquals(RESULT_FQDN, InternetUtils.getLocalHostName());
	}

	@Test
	public void testIPFQDN() throws UnknownHostException, SocketException {
		PowerMockito.when(myAddress.getHostName()).thenReturn(RESULT_IP);
		PowerMockito.when(myAddress.getCanonicalHostName()).thenReturn(RESULT_FQDN);
		Assert.assertEquals(RESULT_FQDN, InternetUtils.getLocalHostName());
	}
}
