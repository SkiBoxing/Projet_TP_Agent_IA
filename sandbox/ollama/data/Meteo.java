package sandbox.ollama.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service météo utilisant l'API OpenWeatherMap
 * Fournit des méthodes pour récupérer et analyser les données météo
 * avec une gestion robuste des erreurs et des entrées utilisateur.
 *
 * @author Claude.AI (adapté par E.ADAM)
 */
public class Meteo {

    private static final String API_KEY = "12345678912346579123";// Remplacez par votre clé API
    private static final String BASE_URL = "http://api.openweathermap.org/data/2.5/weather";
    private static final Logger logger = Logger.getLogger(Meteo.class.getName());

    /**
     * Nettoie et valide le nom d'une ville
     *
     * @param cityName nom de la ville à nettoyer
     * @return nom nettoyé ou null si invalide
     */
    private String cleanCityName(String cityName) {
        if (cityName == null) {
            return null;
        }

        // Supprimer les espaces en début/fin et les caractères de contrôle
        String cleaned = cityName.trim().replaceAll("\\p{Cntrl}", "");

        // Vérifier que le nom n'est pas vide après nettoyage
        if (cleaned.isEmpty()) {
            return null;
        }

        // Vérifier la longueur (raisonnable pour un nom de ville)
        if (cleaned.length() > 100) {
            logger.warning("Nom de ville trop long: " + cleaned.length() + " caractères");
            return null;
        }

        return cleaned;
    }

    /**
     * Récupère les données météo pour une ville donnée
     *
     * @param cityName nom de la ville
     * @return WeatherData ou null en cas d'erreur
     */
    public WeatherData getWeatherByCity(String cityName) {
        String cleanedCityName = cleanCityName(cityName);
        if (cleanedCityName == null) {
            logger.warning("Nom de ville invalide: " + cityName);
            return null;
        }

        try {
            String encodedCityName = URLEncoder.encode(cleanedCityName, StandardCharsets.UTF_8);
            String urlString = String.format("%s?q=%s&appid=%s&units=metric&lang=fr",
                    BASE_URL, encodedCityName, API_KEY);

            logger.info("URL générée: " + urlString);
            String jsonResponse = makeHttpRequest(urlString);
            if (jsonResponse != null) {
                return parseWeatherData(jsonResponse);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors de la récupération des données pour la ville: " + cleanedCityName, e);
        }

        return null;
    }

    /**
     * Récupère les données météo par coordonnées
     *
     * @param lat latitude
     * @param lon longitude
     * @return WeatherData ou null en cas d'erreur
     */
    public WeatherData getWeatherByCoordinates(double lat, double lon) {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            logger.warning("Coordonnées invalides: lat=" + lat + ", lon=" + lon);
            return null;
        }

        try {
            String urlString = String.format("%s?lat=%f&lon=%f&appid=%s&units=metric&lang=fr",
                    BASE_URL, lat, lon, API_KEY);

            String jsonResponse = makeHttpRequest(urlString);
            if (jsonResponse != null) {
                return parseWeatherData(jsonResponse);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors de la récupération des données pour les coordonnées: " + lat + "," + lon, e);
        }

        return null;
    }

    /**
     * Effectue la requête HTTP avec validation d'URL
     *
     * @param urlString URL à interroger
     * @return réponse JSON ou null en cas d'erreur
     */
    private String makeHttpRequest(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // Validation de l'URL avant de l'utiliser
            if (urlString == null || urlString.trim().isEmpty()) {
                logger.warning("URL vide ou null");
                return null;
            }

            // Log de debug pour voir l'URL générée
            logger.info("Tentative de connexion à: " + urlString);

            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "WeatherService/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                return response.toString();

            } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                logger.severe("Clé API invalide ou manquante");
                return null;
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                logger.warning("Ville non trouvée (HTTP 404)");
                return null;
            } else {
                logger.warning("Erreur HTTP: " + responseCode);
                return null;
            }

        } catch (java.net.MalformedURLException e) {
            logger.log(Level.SEVERE, "URL mal formée: " + urlString, e);
            return null;
        } catch (java.net.UnknownHostException e) {
            logger.log(Level.SEVERE, "Impossible de résoudre l'hôte (vérifiez votre connexion Internet)", e);
            return null;
        } catch (java.net.SocketTimeoutException e) {
            logger.log(Level.WARNING, "Timeout lors de la connexion", e);
            return null;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors de la requête HTTP vers: " + urlString, e);
            return null;
        } finally {
            // Nettoyage des ressources
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Erreur lors de la fermeture du reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parse les données JSON de l'API météo avec org.json
     *
     * @param jsonResponse réponse JSON de l'API
     * @return WeatherData ou null en cas d'erreur
     */
    private WeatherData parseWeatherData(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            logger.warning("Réponse JSON vide");
            return null;
        }

        try {
            JSONObject json = new JSONObject(jsonResponse);

            // Vérifier si la réponse contient une erreur
            if (json.has("cod")) {
                int cod = json.getInt("cod");
                if (cod != 200) {
                    String message = json.optString("message", "Erreur inconnue");
                    logger.warning("Erreur API: " + cod + " - " + message);
                    return null;
                }
            }

            WeatherData weatherData = new WeatherData();

            // Informations de base
            weatherData.setCityName(json.optString("name", "N/A"));
            JSONObject sys = json.optJSONObject("sys");
            if (sys != null) {
                weatherData.setCountry(sys.optString("country", "N/A"));
                weatherData.setSunrise(sys.optLong("sunrise", 0) * 1000);
                weatherData.setSunset(sys.optLong("sunset", 0) * 1000);
            }

            // Température
            JSONObject main = json.optJSONObject("main");
            if (main != null) {
                weatherData.setTemperature(main.optDouble("temp", 0.0));
                weatherData.setFeelsLike(main.optDouble("feels_like", 0.0));
                weatherData.setTempMin(main.optDouble("temp_min", 0.0));
                weatherData.setTempMax(main.optDouble("temp_max", 0.0));
                weatherData.setHumidity(main.optInt("humidity", 0));
                weatherData.setPressure(main.optInt("pressure", 0));
            }

            // Conditions météo
            JSONArray weatherArray = json.optJSONArray("weather");
            if (weatherArray != null && !weatherArray.isEmpty()) {
                JSONObject weather = weatherArray.getJSONObject(0);
                weatherData.setDescription(weather.optString("description", "N/A"));
                weatherData.setMainCondition(weather.optString("main", "N/A"));
            }

            // Vent
            JSONObject wind = json.optJSONObject("wind");
            if (wind != null) {
                weatherData.setWindSpeed(wind.optDouble("speed", 0.0));
                weatherData.setWindDirection(wind.optInt("deg", 0));
            }

            // Visibilité
            weatherData.setVisibility(json.optInt("visibility", 0));

            // Coordonnées
            JSONObject coord = json.optJSONObject("coord");
            if (coord != null) {
                weatherData.setLatitude(coord.optDouble("lat", 0.0));
                weatherData.setLongitude(coord.optDouble("lon", 0.0));
            }

            // Timestamps
            weatherData.setTimestamp(json.optLong("dt", 0) * 1000);

            return weatherData;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Erreur lors du parsing JSON: " + jsonResponse, e);
            return null;
        }
    }

    /**
     * Classe pour stocker les données météo
     */
    public static class WeatherData {
        private String cityName;
        private String country;
        private double temperature;
        private double feelsLike;
        private double tempMin;
        private double tempMax;
        private int humidity;
        private int pressure;
        private String description;
        private String mainCondition;
        private double windSpeed;
        private int windDirection;
        private int visibility;
        private double latitude;
        private double longitude;
        private long timestamp;
        private long sunrise;
        private long sunset;

        // Getters et Setters
        public String getCityName() {
            return cityName;
        }

        public void setCityName(String cityName) {
            this.cityName = cityName;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public double getFeelsLike() {
            return feelsLike;
        }

        public void setFeelsLike(double feelsLike) {
            this.feelsLike = feelsLike;
        }

        public double getTempMin() {
            return tempMin;
        }

        public void setTempMin(double tempMin) {
            this.tempMin = tempMin;
        }

        public double getTempMax() {
            return tempMax;
        }

        public void setTempMax(double tempMax) {
            this.tempMax = tempMax;
        }

        public int getHumidity() {
            return humidity;
        }

        public void setHumidity(int humidity) {
            this.humidity = humidity;
        }

        public int getPressure() {
            return pressure;
        }

        public void setPressure(int pressure) {
            this.pressure = pressure;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getMainCondition() {
            return mainCondition;
        }

        public void setMainCondition(String mainCondition) {
            this.mainCondition = mainCondition;
        }

        public double getWindSpeed() {
            return windSpeed;
        }

        public void setWindSpeed(double windSpeed) {
            this.windSpeed = windSpeed;
        }

        public int getWindDirection() {
            return windDirection;
        }

        public void setWindDirection(int windDirection) {
            this.windDirection = windDirection;
        }

        public int getVisibility() {
            return visibility;
        }

        public void setVisibility(int visibility) {
            this.visibility = visibility;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public long getSunrise() {
            return sunrise;
        }

        public void setSunrise(long sunrise) {
            this.sunrise = sunrise;
        }

        public long getSunset() {
            return sunset;
        }

        public void setSunset(long sunset) {
            this.sunset = sunset;
        }

        /**
         * Méthode utilitaire pour formater la direction du vent
         */
        public String getWindDirectionText() {
            if (windDirection >= 337.5 || windDirection < 22.5) return "Nord";
            else if (windDirection < 67.5) return "Nord-Est";
            else if (windDirection < 112.5) return "Est";
            else if (windDirection < 157.5) return "Sud-Est";
            else if (windDirection < 202.5) return "Sud";
            else if (windDirection < 247.5) return "Sud-Ouest";
            else if (windDirection < 292.5) return "Ouest";
            else return "Nord-Ouest";
        }

        /**
         * Convertit la vitesse du vent en km/h
         */
        public double getWindSpeedKmh() {
            return windSpeed * 3.6;
        }

        /**
         * Vérifie si les données météo sont valides
         */
        public boolean isValid() {
            return cityName != null && !cityName.equals("N/A") &&
                    !description.equals("N/A") && temperature != 0.0;
        }

        @Override
        public String toString() {
            if (!isValid()) {
                return "Données météo non disponibles";
            }

            return String.format(
                    "Météo à %s, %s (%.4f, %.4f):\n" +
                            "Temperature: %.1f°C (ressenti: %.1f°C)\n" +
                            "Min/Max: %.1f°C / %.1f°C\n" +
                            "Conditions: %s (%s)\n" +
                            "Humidité: %d%%\n" +
                            "Pression: %d hPa\n" +
                            "Vent: %.1f m/s (%.1f km/h), direction %d° (%s)\n" +
                            "Visibilité: %d m\n" +
                            "Lever/Coucher du soleil: %tT / %tT",
                    cityName, country, latitude, longitude,
                    temperature, feelsLike, tempMin, tempMax,
                    description, mainCondition, humidity, pressure,
                    windSpeed, getWindSpeedKmh(), windDirection, getWindDirectionText(),
                    visibility, new java.util.Date(sunrise), new java.util.Date(sunset)
            );
        }
    }

    /**
     * Méthode utilitaire pour obtenir des informations météo formatées
     *
     * @param cityName nom de la ville
     * @return String formaté avec les données météo ou message d'erreur
     */
    public String getFormattedWeather(String cityName) {
        WeatherData weather = getWeatherByCity(cityName);
        if (weather != null && weather.isValid()) {
            return weather.toString();
        } else {
            return "Impossible de récupérer les données météo pour " + cityName;
        }
    }

    /**
     * Vérifie si les conditions sont favorables (pour un agent)
     *
     * @param cityName nom de la ville
     * @return true si favorables, false sinon ou en cas d'erreur
     */
    public boolean isWeatherFavorable(String cityName) {
        WeatherData weather = getWeatherByCity(cityName);
        if (weather == null || !weather.isValid()) {
            logger.warning("Impossible de vérifier les conditions météo pour: " + cityName);
            return false;
        }

        // Conditions considérées comme favorables
        return weather.getTemperature() >= 15 && weather.getTemperature() <= 25
                && weather.getWindSpeed() < 10
                && !weather.getMainCondition().toLowerCase().contains("rain")
                && !weather.getMainCondition().toLowerCase().contains("storm");
    }

    /**
     * Obtient un conseil météo pour un agent
     *
     * @param cityName nom de la ville
     * @return conseil sous forme de String
     */
    public String getWeatherAdvice(String cityName) {
        WeatherData weather = getWeatherByCity(cityName);
        if (weather == null || !weather.isValid()) {
            return "Données météo non disponibles pour " + cityName;
        }

        StringBuilder advice = new StringBuilder();

        if (weather.getTemperature() < 0) {
            advice.append("⚠️ Attention au gel ! Prévoyez des vêtements chauds. ");
        } else if (weather.getTemperature() > 30) {
            advice.append("🌡️ Il fait très chaud, pensez à vous hydrater. ");
        }

        if (weather.getMainCondition().toLowerCase().contains("rain")) {
            advice.append("🌧️ Il pleut, n'oubliez pas votre parapluie ! ");
        } else if (weather.getMainCondition().toLowerCase().contains("snow")) {
            advice.append("❄️ Il neige, attention aux routes glissantes. ");
        }

        if (weather.getWindSpeed() > 15) {
            advice.append("💨 Vent fort (").append(String.format("%.1f", weather.getWindSpeedKmh())).append(" km/h). ");
        }

        if (weather.getHumidity() > 80) {
            advice.append("💧 Humidité élevée (").append(weather.getHumidity()).append("%). ");
        }

        if (advice.isEmpty()) {
            advice.append(" Conditions météo stables !");
        }

        return advice.toString().trim();
    }


    /**
     * Méthode pour tester les services de l'API météo
     */
    static void testComplet() {
        Meteo service = new Meteo();
        Scanner scanner = new Scanner(System.in);

        System.out.print("Entrez le nom d'une ville: ");
        String city = scanner.nextLine();

        // Test des différentes méthodes
        WeatherData weather = service.getWeatherByCity(city);
        if (weather != null && weather.isValid()) {
            System.out.println("\n" + weather);

            // Test des conditions favorables
            boolean favorable = service.isWeatherFavorable(city);
            System.out.println("\nConditions favorables: " + (favorable ? "OUI" : "NON"));

            // Conseil météo
            String advice = service.getWeatherAdvice(city);
            System.out.println("Conseil: " + advice);

        } else {
            System.out.println("Impossible de récupérer les données météo pour " + city);
        }

        // Exemple avec des coordonnées (Paris)
        System.out.println("\n--- Test avec coordonnées (Queenstown, Tasmanie) ---");
        WeatherData parisWeather = service.getWeatherByCoordinates(-49.92937296986032, -73.4428738818008);
        if (parisWeather != null && parisWeather.isValid()) {
            System.out.println(parisWeather);
        } else {
            System.out.println("Erreur avec les coordonnées de Paris");
        }

        scanner.close();
    }


    static String getNatureTemperature(String town) {
        Meteo service = new Meteo();
        WeatherData weather = service.getWeatherByCity(town);
        if (weather != null && weather.isValid()) {
            double temp = weather.getTemperature();
            if (temp < 0) {
                return "très froid";
            } else if (temp < 10) {
                return "froid";
            } else if (temp < 17) {
                return "tempéré";
            } else if (temp < 26) {
                return "chaud";
            } else if (temp < 35) {
                return "très chaud";
            } else {
                return "extrêmement chaud";
            }
        } else {
            return "données météo non disponibles";
        }
    }


    public static void main(String[] args) {
        String town = "Valenciennes";
        System.out.println("meteo de " + town + " : " + getNatureTemperature(town));
        town = "Belém";
        System.out.println("meteo de " + town + " : " + getNatureTemperature(town));
        town = "Alep";
        System.out.println("meteo de " + town + " : " + getNatureTemperature(town));
    }
}