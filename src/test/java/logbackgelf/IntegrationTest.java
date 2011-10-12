package logbackgelf;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Random;
import java.util.jar.JarEntry;

public class IntegrationTest {

    private static final String longMessage = createLongMessage();

    private static String createLongMessage() {
        Random rand = new Random();
        StringBuffer str = new StringBuffer();
        for (int i=0; i< 1000; i++) {
            char theChar = (char)(rand.nextInt(30) + 65);
            for (int j=0; j < 80; j++) {
                str.append(theChar);
            }
            str.append('\n');
        }
        return str.toString();
    }

    @Test
    public void test() {

        Logger logger = LoggerFactory.getLogger(this.getClass());

        MDC.put("ipAddress", "87.345.23.55");

        logger.debug("this is a new test");
        logger.debug("this is a test with ({}) parameter", "this");
        logger.debug("This is a test with a really long ending: " + longMessage);
        try {
            throw new IllegalStateException("Expected exception") ;
        } catch (Exception e) {
            logger.error("expected error", e);
        }
    }

}
