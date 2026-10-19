package meteo;

import data.JourneysList;

/**
 * Adapts a catalog of journeys to the current weather of the traveller's city:
 * <ul>
 *     <li>rain and/or very strong wind: bike journeys are not proposed anymore,</li>
 *     <li>strong wind (not very strong): bike journeys are proposed with a duration majored by 50%,</li>
 *     <li>snow: car journeys are proposed with a duration majored by 50% and a cost majored by 20%.</li>
 * </ul>
 *
 * @author ANGRAND
 */
public final class ClimateRules {

    /**
     * wind speed (in m/s) above which the wind is considered strong
     */
    private static final double STRONG_WIND_MS = 8.0;
    /**
     * wind speed (in m/s) above which the wind is considered very strong
     */
    private static final double VERY_STRONG_WIND_MS = 15.0;
    private static final double BIKE_DURATION_FACTOR_WIND = 1.5;
    private static final double CAR_DURATION_FACTOR_SNOW = 1.5;
    private static final double CAR_COST_FACTOR_SNOW = 1.2;

    private ClimateRules() {
    }

    /**
     * adapt, in place, a catalog of journeys to the given weather
     *
     * @param catalog catalog of journeys to adapt
     * @param weather current weather of the traveller's city
     * @return a human-readable summary of the adaptations applied, or null if none was applied
     */
    public static String applyToCatalog(JourneysList catalog, WeatherData weather) {
        if (catalog == null || weather == null) return null;

        StringBuilder summary = new StringBuilder();
        boolean veryStrongWind = weather.getWindSpeed() >= VERY_STRONG_WIND_MS;
        boolean strongWind = weather.getWindSpeed() >= STRONG_WIND_MS;

        if (weather.isRaining() || veryStrongWind) {
            catalog.removeIf(j -> "bike".equalsIgnoreCase(j.getMeans()));
            summary.append("pluie et/ou vent tres fort : trajets a velo non proposes. ");
        } else if (strongWind) {
            catalog.adjustIf(j -> "bike".equalsIgnoreCase(j.getMeans()),
                    j -> j.applyDurationFactor(BIKE_DURATION_FACTOR_WIND));
            summary.append("vent fort : duree des trajets a velo majoree de 50%. ");
        }

        if (weather.isSnowing()) {
            catalog.adjustIf(j -> "car".equalsIgnoreCase(j.getMeans()), j -> {
                j.applyDurationFactor(CAR_DURATION_FACTOR_SNOW);
                j.applyCostFactor(CAR_COST_FACTOR_SNOW);
            });
            summary.append("neige : duree des trajets en voiture majoree de 50%, cout majore de 20%. ");
        }

        return summary.isEmpty() ? null : summary.toString();
    }
}
