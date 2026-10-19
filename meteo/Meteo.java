package meteo;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client of the OpenWeatherMap "current weather" api (free plan, 60 calls/minute).
 * Register for free at <a href="https://openweathermap.org/api">openweathermap.org/api</a> and set your own
 * key in {@link #API_KEY}.
 *
 * @author ANGRAND
 */
public class Meteo {

    /**
     * api key, to be replaced by your own key (free registration on openweathermap.org)
     */
    private static final String API_KEY = "04c6c445aa08505a9fe61d39b62559e6";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final Logger logger = Logger.getLogger(Meteo.class.getName());

    /**
     * fetch the current weather of a city
     *
     * @param cityName name of the city
     * @return the weather data, or null if the city is unknown or the service is unreachable
     */
    public WeatherData getCurrentWeather(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            logger.warning("nom de ville invalide: " + cityName);
            return null;
        }
        String cleanedCityName = cityName.trim();

        String urlString = BASE_URL + "?q=" + URLEncoder.encode(cleanedCityName, StandardCharsets.UTF_8)
                + "&appid=" + API_KEY + "&units=metric&lang=fr";
        String jsonResponse = makeHttpRequest(urlString);
        return jsonResponse == null ? null : parseWeatherData(jsonResponse);
    }

    /**
     * performs the http GET request toward the api
     *
     * @param urlString url to call
     * @return the json response as a String, or null in case of error
     */
    private String makeHttpRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(urlString);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.warning("erreur http " + responseCode + " en appelant l'api meteo");
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                return response.toString();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "impossible de contacter le service meteo", e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * extract the fields needed from the json response of the api
     *
     * @param jsonResponse raw json response
     * @return the weather data, or null if the response cannot be parsed
     */
    private WeatherData parseWeatherData(String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            if (json.optInt("cod", 200) != 200) {
                logger.warning("erreur api meteo: " + json.optString("message", "erreur inconnue"));
                return null;
            }
            String cityName = json.optString("name", "?");
            double temperature = json.optJSONObject("main") != null ? json.getJSONObject("main").optDouble("temp", 0) : 0;
            double windSpeed = json.optJSONObject("wind") != null ? json.getJSONObject("wind").optDouble("speed", 0) : 0;
            String mainCondition = "?";
            String description = "?";
            var weatherArray = json.optJSONArray("weather");
            if (weatherArray != null && !weatherArray.isEmpty()) {
                var weather = weatherArray.getJSONObject(0);
                mainCondition = weather.optString("main", "?");
                description = weather.optString("description", "?");
            }
            return new WeatherData(cityName, temperature, windSpeed, mainCondition, description);
        } catch (Exception e) {
            logger.log(Level.WARNING, "reponse meteo illisible: " + jsonResponse, e);
            return null;
        }
    }

    public static void main(String[] args) {
        Meteo meteo = new Meteo();
        WeatherData weather = meteo.getCurrentWeather("Valenciennes");
        System.out.println(weather == null ? "donnees meteo indisponibles" : weather);
    }
}
