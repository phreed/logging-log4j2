package org.apache.logging.log4j.core.appender.rolling;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.test.junit.CleanFolders;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Tests https://issues.apache.org/jira/browse/LOG4J2-1798
 */
public class RollingFileAppenderInterruptedThreadTest {

    private static final String ROLLING_APPENDER_FILES_DIR =
            "target/" + RollingFileAppenderInterruptedThreadTest.class.getSimpleName();

    @Rule
    public CleanFolders cleanFolders = new CleanFolders(true, false, 3, ROLLING_APPENDER_FILES_DIR);

    LoggerContext loggerContext;

    @Before
    public void setUp() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setConfigurationName("LOG4J2-1798 test");

        builder.add(builder.newAppender("consoleLog", "Console")
                .addAttribute("target", ConsoleAppender.Target.SYSTEM_ERR));

        builder.add(builder.newAppender("fileAppender", "RollingFile")
                .addAttribute("filePattern", ROLLING_APPENDER_FILES_DIR + "/file-%i.log")
                .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%msg%n"))
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy")
                        .addAttribute("size", "20B"))); // relatively small amount to trigger rotation quickly

        builder.add(builder.newRootLogger(Level.INFO)
                .add(builder.newAppenderRef("consoleLog"))
                .add(builder.newAppenderRef("fileAppender")));

        loggerContext = Configurator.initialize(builder.build());
    }

    @After
    public void tearDown() {
        Configurator.shutdown(loggerContext);
        loggerContext = null;
    }

    @Test
    public void testRolloverInInterruptedThread() {
        Logger logger = loggerContext.getLogger(getClass().getName());

        Assert.assertThat(logger.getAppenders().values(), hasItem(instanceOf(RollingFileAppender.class)));

        logger.info("Sending logging event 1"); // send first event to initialize rollover system

        Thread.currentThread().interrupt(); // mark thread as interrupted
        logger.info("Sending logging event 2"); // send second event to trigger rotation, expecting 2 files in result

        Assert.assertTrue(new File(ROLLING_APPENDER_FILES_DIR, "file-1.log").exists());
        Assert.assertTrue(new File(ROLLING_APPENDER_FILES_DIR, "file-2.log").exists());
    }
}
