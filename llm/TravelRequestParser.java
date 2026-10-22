package llm;

import org.json.JSONObject;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * turns a natural language travel request (e.g. "je veux aller de a à d avant 15h avec le moins de CO2 possible")
 * into a {@link TravelRequest}, using a local LLM served by Ollama.
 *
 * @author ANGRAND
 */
public class TravelRequestParser {

    private static final Set<String> KNOWN_ZONES = Set.of("a", "b", "c", "d", "e", "f");
    private static final Set<String> KNOWN_PREFERENCES = Set.of("cost", "co2", "confort", "duration", "duration-cost");
    private static final int DEFAULT_DEPARTURE_TIME = 800;
    private static final String DEFAULT_PREFERENCE = "cost";
    private static final Logger logger = Logger.getLogger(TravelRequestParser.class.getName());

    private final OllamaClient client;
    private final String model;

    public TravelRequestParser() {
        this.client = new OllamaClient();
        this.model = client.pickAvailableModel();
    }

    /**
     * @param naturalLanguageRequest free text request from the traveller
     * @return the extracted travel request, or null if the LLM is unreachable or its answer cannot be interpreted
     */
    public TravelRequest parse(String naturalLanguageRequest) {
        if (naturalLanguageRequest == null || naturalLanguageRequest.isBlank()) return null;

        String prompt = """
                Tu extrais une demande de trajet depuis un texte en langage naturel.
                Le reseau de transport ne connait que 6 zones nommees a, b, c, d, e, f.
                Reponds UNIQUEMENT par un objet JSON, sans aucun texte autour, de la forme exacte :
                {"from": "<zone de depart, une lettre parmi a a f>", "to": "<zone d'arrivee, une lettre parmi a a f>", "departureTime": <heure de depart souhaitee au format hhmm, ex 1430 pour 14h30>, "preference": "<un choix parmi cost, co2, confort, duration, duration-cost>"}

                Demande : """ + naturalLanguageRequest;

        String response = client.generate(model, prompt);
        if (response == null) return null;

        try {
            String json = response.substring(response.indexOf('{'), response.lastIndexOf('}') + 1);
            JSONObject obj = new JSONObject(json);

            String from = obj.optString("from", "").trim().toLowerCase();
            String to = obj.optString("to", "").trim().toLowerCase();
            if (!KNOWN_ZONES.contains(from) || !KNOWN_ZONES.contains(to)) return null;

            String preference = obj.optString("preference", DEFAULT_PREFERENCE).trim().toLowerCase();
            if (!KNOWN_PREFERENCES.contains(preference)) preference = DEFAULT_PREFERENCE;

            int departureTime = obj.optInt("departureTime", DEFAULT_DEPARTURE_TIME);

            return new TravelRequest(from, to, departureTime, preference);
        } catch (Exception e) {
            logger.log(Level.WARNING, "reponse du LLM non interpretable: " + response, e);
            return null;
        }
    }
}
