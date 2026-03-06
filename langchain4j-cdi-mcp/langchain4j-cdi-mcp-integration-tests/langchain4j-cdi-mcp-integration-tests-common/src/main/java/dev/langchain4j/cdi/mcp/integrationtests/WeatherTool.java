package dev.langchain4j.cdi.mcp.integrationtests;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class WeatherTool {

    @Tool("Get the current weather for a given city")
    public String getWeather(@P("The city name") String city, @P("Unit: celsius or fahrenheit") String unit) {
        return "Sunny, 22C in " + city;
    }
}
