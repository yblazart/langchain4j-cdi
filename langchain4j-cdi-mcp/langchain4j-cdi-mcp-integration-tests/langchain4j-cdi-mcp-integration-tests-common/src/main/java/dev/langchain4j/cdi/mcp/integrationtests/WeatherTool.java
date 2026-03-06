package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.cdi.mcp.server.McpTool;
import dev.langchain4j.cdi.mcp.server.McpToolArg;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WeatherTool {

    @McpTool("Get the current weather for a given city")
    public String getWeather(
            @McpToolArg("The city name") String city, @McpToolArg("Unit: celsius or fahrenheit") String unit) {
        return "Sunny, 22C in " + city;
    }
}
