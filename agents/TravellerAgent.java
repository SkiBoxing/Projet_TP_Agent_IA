package agents;

import jade.core.AID;
import jade.core.AgentServicesTools;
import jade.core.behaviours.ReceiverBehaviour;
import jade.domain.DFSubscriber;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import comportements.ContractNetAchat;
import data.ComposedJourney;
import data.JourneysList;
import gui.TravellerGui;
import llm.TravelRequest;
import llm.TravelRequestParser;
import meteo.ClimateRules;
import meteo.Meteo;
import meteo.WeatherData;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * Journey searcher
 *
 * @author Emmanuel ADAM
 */
public class TravellerAgent extends GuiAgent {
    /**
     * code pour ajout de livre par la gui
     */
    public static final int EXIT = 0;
    /**
     * code pour achat de livre par la gui
     */
    public static final int BUY_TRAVEL = 1;

    /**
     * liste des vendeurs
     */
    private ArrayList<AID> vendeurs;

    /**
     * catalog received by the sellers
     */
    private JourneysList catalogs;

    /**
     * delay in minute between two rides in a junction (in minutes)
     * */
    int delay = 90;

    /**
     * the journey chosen by the agent
     */
    private ComposedJourney myJourney;

    /**
     * city in which the traveller is located, used to check the weather before proposing journeys
     */
    private String city = "Valenciennes";

    /**
     * client of the weather api
     */
    private final Meteo meteo = new Meteo();

    /**
     * parser used to interpret a travel request expressed in natural language, via a local LLM
     */
    private final TravelRequestParser requestParser = new TravelRequestParser();

    /**
     * topic from which the alert will be received
     */
    private AID topic;

    /**
     * gui
     */
    private TravellerGui window;

    // Ces attributs servent à mémoriser la demande initiale, c'est utile pour le traitement des alertes
    private String origineDemandee;
    private String destinationDemandee;
    private int heureDemandee;
    private String preferenceDemandee;

    /**
     * Initialisation de l'agent
     */
    @Override
    protected void setup() {
        this.window = new TravellerGui(this);
        window.setColor(Color.cyan);
        window.println("Hello! AgentAcheteurCN " + this.getLocalName() + " est pret. ");
        window.setVisible(true);

        vendeurs = new ArrayList<>();
        detectAgences();

        topic = AgentServicesTools.generateTopicAID(this, "TRAFFIC NEWS");

        //ecoute les messages radio et réagit aux alertes
        addBehaviour(new ReceiverBehaviour(
                this,
                -1,
                MessageTemplate.MatchTopic(topic),
                true,
                (a, m) -> traiterAlerte(m.getContent())
        ));

    }

    /**
     * Réagit en cas d'alerte : détermine si le trajet actuel est impacté ou non par l'alerte.
     * S'il l'est, relance une recherche de trajet afin de trouver un nouveau trajet possible.
     */
    private void traiterAlerte(String contenu) {

        String[] troncon = contenu.split(",", -1);

        if (troncon.length != 2) {
            println("Alerte invalide : " + contenu);
            return;
        }

        String depart = troncon[0].trim();
        String arrivee = troncon[1].trim();

        if (depart.isEmpty() || arrivee.isEmpty()) {
            println("Alerte invalide : " + contenu);
            return;
        }

        println("Alerte trafic : " + depart + " -> " + arrivee);

        if (myJourney == null) {
            println("Aucun itinéraire selectionné.");
            return;
        }

        boolean impacte = myJourney.getJourneys().stream()
                .anyMatch(j -> j.getStart().equalsIgnoreCase(depart) && j.getStop().equalsIgnoreCase(arrivee));

        if (!impacte) {
            println("Mon itinéraire n'est pas impacté.");
            return;
        }

        println("Itinéraire impacté : recherche d'un remplacement.");

        relancerRecherche();
    }

    /**
     * Lance une nouvelle recherche de trajet après réception d'une alerte.
     */
    private void relancerRecherche() {

        if (origineDemandee == null || destinationDemandee == null || preferenceDemandee == null) {
            println("Impossible de relancer la recherche : demande initiale inconnue.");
            return;
        }

        // Invalider l'ancien itinéraire pour ne pas le réutiliser.
        myJourney = null;

        addBehaviour(new ContractNetAchat(
                this,
                new ACLMessage(ACLMessage.CFP),
                origineDemandee,
                destinationDemandee,
                heureDemandee,
                preferenceDemandee
        ));

        println("Nouvelle demande envoyée aux agences.");
    }


    /**
     * ecoute des evenement de type enregistrement en tant qu'agence aupres des pages jaunes
     */
    private void detectAgences() {
        var model = AgentServicesTools.createAgentDescription("travel agency", "seller");
        vendeurs = new ArrayList<>();

        //souscription au service des pages jaunes pour recevoir une alerte en cas mouvement sur le service travel agency'seller
        addBehaviour(new DFSubscriber(this, model) {
            @Override
            public void onRegister(DFAgentDescription dfd) {
                vendeurs.add(dfd.getName());
                window.println(dfd.getName().getLocalName() + " s'est inscrit en tant qu'agence : " + model.getAllServices().get(0));
            }

            @Override
            public void onDeregister(DFAgentDescription dfd) {
                vendeurs.remove(dfd.getName());
                window.println(dfd.getName().getLocalName() + " s'est desinscrit de  : " + model.getAllServices().get(0));
            }

        });

    }

    /**
     * compute a composed journey from a departure to an arrival point
     * @param from       departure point
     * @param to         arrival point
     * @param departure  desired departure time (in hhmm)
     * @param preference preference for the choice of the journey (cost, confort, duration, duration-cost)
     * */
    public void computeComposedJourney(final String from, final String to, final int departure,
                                       final String preference) {
        final List<ComposedJourney> journeys = new ArrayList<>();

        adaptCatalogsToWeather();

        final boolean result = catalogs.findIndirectJourney(from, to, departure, 60, new ArrayList<>(),
                new ArrayList<>(), journeys);

        if (!result) {
            println("no journey found !!!");
        }
        if (result) {
            //oter les voyages demarrant trop tard
            journeys.removeIf(j -> j.getJourneys().getFirst().getDepartureDate() - departure > delay);
            switch (preference) {
                case "duration" -> {
                    journeys.sort(Comparator.comparingDouble(ComposedJourney::getDuration));
                }
                case "confort" -> journeys.sort(Comparator.comparingInt(ComposedJourney::getConfort).reversed());
                case "cost" -> journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
                case "duration-cost" ->
                //        journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
                journeys.sort((j1, j2) -> {
                    var difDuration = j1.getDuration() - j2.getDuration() / Math.max(j2.getDuration(),j1.getDuration());
                    var difCost = j1.getCost() - j2.getCost() / Math.max(j2.getCost(),j1.getCost());
                    return (int)(10*(difDuration + difCost));});
                default -> journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
            }
            myJourney = journeys.getFirst();
            println("I choose this journey : " + myJourney);
        }
    }

    /**
     * get event from the GUI
     */
    @Override
    protected void onGuiEvent(final GuiEvent eventFromGui) {

        if (eventFromGui.getType() == TravellerAgent.EXIT) {
            doDelete();
        }

        if (eventFromGui.getType() == TravellerAgent.BUY_TRAVEL) {

            // récupération de la demande initiale (champs du formulaire)
            origineDemandee = (String) eventFromGui.getParameter(0);
            destinationDemandee = (String) eventFromGui.getParameter(1);
            heureDemandee = (Integer) eventFromGui.getParameter(2);
            preferenceDemandee = (String) eventFromGui.getParameter(3);

            final String givenCity = (String) eventFromGui.getParameter(4);
            if (givenCity != null && !givenCity.isBlank()) city = givenCity;

            // si l'utilisateur a formule sa demande en langage naturel, on tente de l'interpreter via le LLM
            // et on remplace la demande du formulaire par celle comprise par le LLM
            final String naturalRequest = (String) eventFromGui.getParameter(5);
            if (naturalRequest != null && !naturalRequest.isBlank()) {
                final TravelRequest parsed = requestParser.parse(naturalRequest);
                if (parsed != null) {
                    origineDemandee = parsed.from();
                    destinationDemandee = parsed.to();
                    heureDemandee = parsed.departureTime();
                    preferenceDemandee = parsed.preference();
                    println("demande interpretee par le LLM : " + parsed);
                } else {
                    println("demande en langage naturel non comprise, utilisation du formulaire.");
                }
            }

            addBehaviour(new ContractNetAchat(
                    this,
                    new ACLMessage(ACLMessage.CFP),
                    origineDemandee,
                    destinationDemandee,
                    heureDemandee,
                    preferenceDemandee
            ));
        }
    }

    /**
     * fetch the current weather of the traveller's city and adapt the received catalogs accordingly
     * (bike journeys removed/slowed in case of rain or wind, car journeys slowed/more expensive in case of snow)
     */
    private void adaptCatalogsToWeather() {
        final WeatherData weather = meteo.getCurrentWeather(city);
        if (weather == null) {
            println("meteo indisponible pour " + city + ", pas d'adaptation des trajets.");
            return;
        }
        println("meteo a " + city + " : " + weather.getDescription() + ", vent " + weather.getWindSpeed() + " m/s.");
        final String adaptation = ClimateRules.applyToCatalog(catalogs, weather);
        if (adaptation != null) println("adaptation des trajets : " + adaptation);
    }

    // 'Nettoyage' de l'agent
    @Override
    protected void takeDown() {
        if (window != null) {
            window.dispose();
            System.out.println(getLocalName() + ">>> I leave the platform. ");
        }
    }

    ///// SETTERS AND GETTERS

    /**
     * @return agent gui
     */
    public TravellerGui getWindow() {
        return window;
    }


    /**
     * @return the vendeurs
     */
    public List<AID> getVendeurs() {
        return (ArrayList<AID>) vendeurs.clone();
    }


    /**
     * print a message on the window lined to the agent
     *
     * @param msg text to display in th window
     */
    public void println(final String msg) {
        window.println(msg);
    }

    /**
     * set the list of journeys
     */
    public void setCatalogs(final JourneysList catalogs) {
        this.catalogs = catalogs;
    }


    public ComposedJourney getMyJourney() {
        return myJourney;
    }

}
