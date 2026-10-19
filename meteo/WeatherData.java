package meteo;

/**
 * Weather conditions of a city, as returned by the OpenWeatherMap "current weather" endpoint.
 *
 * @author ANGRAND
 */
public class WeatherData {
    /**
     * name of the city
     */
    private final String cityName;
    /**
     * temperature in Celsius degrees
     */
    private final double temperature;
    /**
     * wind speed in m/s
     */
    private final double windSpeed;
    /**
     * main weather group, as given by OpenWeatherMap (Clear, Clouds, Rain, Drizzle, Thunderstorm, Snow, ...)
     */
    private final String mainCondition;
    /**
     * human-readable description of the weather (in french)
     */
    private final String description;

    public WeatherData(String cityName, double temperature, double windSpeed, String mainCondition, String description) {
        this.cityName = cityName;
        this.temperature = temperature;
        this.windSpeed = windSpeed;
        this.mainCondition = mainCondition;
        this.description = description;
    }

    public String getCityName() {
        return cityName;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public String getMainCondition() {
        return mainCondition;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @return true if it is raining, drizzling or storming in the city
     */
    public boolean isRaining() {
        return "Rain".equalsIgnoreCase(mainCondition) || "Drizzle".equalsIgnoreCase(mainCondition)
                || "Thunderstorm".equalsIgnoreCase(mainCondition);
    }

    /**
     * @return true if it is snowing in the city
     */
    public boolean isSnowing() {
        return "Snow".equalsIgnoreCase(mainCondition);
    }

    @Override
    public String toString() {
        return "meteo a " + cityName + " : " + description + ", " + temperature + "C, vent " + windSpeed + "m/s";
    }
}
