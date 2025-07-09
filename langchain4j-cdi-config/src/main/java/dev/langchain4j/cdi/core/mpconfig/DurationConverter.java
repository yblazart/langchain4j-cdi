/**
 *
 */
package dev.langchain4j.cdi.core.mpconfig;

import java.time.Duration;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * @author Buhake Sindi
 * @since 09 July 2025
 */
public class DurationConverter implements Converter<Duration> {

    @Override
    public Duration convert(String value) throws IllegalArgumentException, NullPointerException {
        // TODO Auto-generated method stub
        String durationString = value.toUpperCase();
        if (!durationString.startsWith("PT"))
            durationString = "PT" + durationString;
        return Duration.parse(durationString);
    }
}
