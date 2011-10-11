package logbackgelf;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class FullTest {

    @Test
    public void test() {

        Logger logger = LoggerFactory.getLogger(this.getClass());

        MDC.put("ipAddress", "87.345.23.52");

        logger.debug("this is a test");
        logger.debug("this is a test with ({}) parameter", "this");
        try {
            throw new IllegalStateException("Expected exception") ;
        } catch (Exception e) {
            logger.error("expected error", e);
        }
    }
}
