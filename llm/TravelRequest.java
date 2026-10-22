package llm;

/**
 * a travel request extracted from a natural language sentence
 *
 * @param from          departure zone (a..f)
 * @param to            arrival zone (a..f)
 * @param departureTime desired departure time, format hhmm
 * @param preference    criteria used to choose the journey (cost, co2, confort, duration, duration-cost)
 */
public record TravelRequest(String from, String to, int departureTime, String preference) {
}
