# TP : Programmation Orienté Agent  
**ANGRAND Louis, DANLOS Ludovic**

# Voyages en ville

## État d'avancement

4 améliorations proposées par le sujet

| Amélioration demandée | Statut | Implémentation |
| --- | --- | --- |
| Décrémentation du nb de places (achat et cas du vélo) | fait | `ContractNetVente.removeTicket`, `JourneysList.findIndirectJourney` |
| Prise en compte de la météo | fait | package `meteo`, `TravellerAgent.adaptCatalogsToWeather` |
| Réaction à une alerte (agence et client) | fait | `AgenceAgent.traiterAlerte`, `TravellerAgent.traiterAlerte` / `relancerRecherche` |
| Demande en langage naturel via LLM | fait — version basique | package `llm`, `TravellerAgent.onGuiEvent` |

## Architecture générale

Le code reste organisé par rôle : `agents` (les trois `GuiAgent` JADE), `comportements` (le protocole ContractNet côté agence et côté voyageur), `data` (le catalogue de trajets et leur composition), `gui` (les fenêtres Swing), et deux packages ajoutés pour les améliorations : `meteo` (client OpenWeatherMap et règles d'adaptation au climat) et `llm` (client Ollama et extraction d'une demande de trajet depuis du texte libre).

Le diagramme de classes ci-dessous se limite, comme demandé, aux agents et à leurs comportements, en indiquant leurs principales dépendances vers ces deux packages.

**Diagramme de classes — agents & comportements**

```mermaid
classDiagram
    direction LR

    class Agent {
        <<jade.core>>
    }
    class GuiAgent {
        <<jade.gui>>
    }
    Agent <|-- GuiAgent

    class AgenceAgent {
        -JourneysList catalog
        -AgenceGui window
        -AID topic
        +setup()
        +onGuiEvent(GuiEvent)
        -fromCSV2Catalog(String)
        -repeatJourney(int, int, int, Journey)
        -traiterAlerte(String)
        +println(String)
    }

    class TravellerAgent {
        -JourneysList catalogs
        -ComposedJourney myJourney
        -String city
        -Meteo meteo
        -TravelRequestParser requestParser
        -TravellerGui window
        -String origineDemandee
        -String destinationDemandee
        -int heureDemandee
        -String preferenceDemandee
        +setup()
        +onGuiEvent(GuiEvent)
        +computeComposedJourney(String, String, int, String)
        -adaptCatalogsToWeather()
        -traiterAlerte(String)
        -relancerRecherche()
        +getMyJourney() ComposedJourney
        +setCatalogs(JourneysList)
    }

    class AlertAgent {
        -AlertGui window
        -AID topic
        +setup()
        +onGuiEvent(GuiEvent)
    }

    GuiAgent <|-- AgenceAgent
    GuiAgent <|-- TravellerAgent
    GuiAgent <|-- AlertAgent

    class ContractNetResponder {
        <<jade.proto>>
    }
    class ContractNetInitiator {
        <<jade.proto>>
    }

    class ContractNetVente {
        -JourneysList catalog
        -AgenceGui window
        +handleCfp(ACLMessage) ACLMessage
        +handleAcceptProposal(ACLMessage, ACLMessage, ACLMessage) ACLMessage
        -removeTicket(Journey)
        +handleRejectProposal(ACLMessage, ACLMessage, ACLMessage)
    }

    class ContractNetAchat {
        -String from
        -String to
        -int departure
        -String preference
        -TravellerAgent monAgent
        +handleRefuse(ACLMessage)
        +handleFailure(ACLMessage)
        +handleAllResponses(List, List)
        +handleInform(ACLMessage)
    }

    ContractNetResponder <|-- ContractNetVente
    ContractNetInitiator <|-- ContractNetAchat

    AgenceAgent "1" *-- "1" ContractNetVente : comportement
    TravellerAgent "1" *-- "0..*" ContractNetAchat : comportement

    AgenceAgent "1" o-- "1" JourneysList : catalog
    TravellerAgent "1" o-- "0..1" JourneysList : catalogs
    TravellerAgent "1" o-- "0..1" ComposedJourney : myJourney
    JourneysList "1" o-- "*" Journey
    ComposedJourney "1" o-- "*" Journey

    TravellerAgent "1" *-- "1" Meteo : meteo
    Meteo ..> WeatherData : crée
    ClimateRules ..> WeatherData : utilise
    ClimateRules ..> JourneysList : adapte

    TravellerAgent "1" *-- "1" TravelRequestParser : requestParser
    TravelRequestParser "1" *-- "1" OllamaClient
    TravelRequestParser ..> TravelRequest : crée
```

**Figure 1.** Les trois `GuiAgent` et leurs comportements ContractNet ; en pointillés, les dépendances ajoutées pour la météo et le LLM.

## Diagrammes de séquence

Les trois échanges clés sont détaillés séparément : la recherche et l'achat d'un voyage (protocole ContractNet complet), l'appel à l'API météo qui précède la recherche d'itinéraire, et l'appel au LLM lorsqu'une demande est formulée en langage naturel.

Fig. 2Recherche et achat d'un voyage

```mermaid
sequenceDiagram
    actor Utilisateur
    participant GUI as TravellerGui
    participant TA as TravellerAgent
    participant CNA as ContractNetAchat
    participant AgA as AgenceAgent (vélo/bus/tram/voiture)
    participant CNV as ContractNetVente
    participant Cat as JourneysList (catalogue agrégé)

    Utilisateur->>GUI: saisit from/to/heure/critère + clique "Buy"
    GUI->>TA: postGuiEvent(BUY_TRAVEL, params)
    TA->>TA: onGuiEvent() : mémorise la demande
    TA->>CNA: new ContractNetAchat(from, to, heure, critère)
    activate CNA
    CNA->>AgA: ACLMessage CFP (diffusé à chaque agence)
    activate AgA
    AgA->>CNV: handleCfp(cfp)
    CNV-->>AgA: PROPOSE(catalogue local)
    deactivate AgA
    AgA-->>CNA: PROPOSE(catalogue local)
    CNA->>CNA: handleAllResponses() : fusionne les catalogues reçus
    CNA->>Cat: addJourneys(catalogue de l'agence)
    CNA->>TA: setCatalogs(catalogs)
    CNA->>TA: computeComposedJourney(from, to, heure, critère)
    activate TA
    TA->>TA: adaptCatalogsToWeather() (voir figure 3)
    TA->>Cat: findIndirectJourney(from, to, heure, ...)
    Cat-->>TA: liste de ComposedJourney possibles
    TA->>TA: tri selon le critère, choix du meilleur => myJourney
    deactivate TA
    alt myJourney trouvé
        CNA->>AgA: ACCEPT_PROPOSAL(billets à acheter) / REJECT_PROPOSAL(autres)
        activate AgA
        AgA->>CNV: handleAcceptProposal(accept)
        CNV->>CNV: removeTicket(journey) pour chaque billet (décrémente les places)
        CNV-->>AgA: INFORM("ok pour ces n tickets")
        deactivate AgA
        AgA-->>CNA: INFORM
        CNA->>TA: handleInform() : affiche la confirmation
    else aucun trajet trouvé
        CNA->>TA: println("aucun trajet acheté : pas d'itinéraire trouvé")
    end
    deactivate CNA
    TA-->>GUI: println(...) : affichage du résultat
```

**Figure 2.** Les agences vélo/bus/tram/voiture sont regroupées sur une seule ligne de vie pour la lisibilité : le même échange CFP / PROPOSE a lieu avec chacune d'elles. La branche `else` couvre le garde-fou ajouté dans `ContractNetAchat` quand aucun itinéraire n'est trouvé.

Fig. 3Appel à l'API météo

```mermaid
sequenceDiagram
    participant TA as TravellerAgent
    participant M as Meteo
    participant OWM as API OpenWeatherMap
    participant CR as ClimateRules
    participant Cat as JourneysList

    TA->>TA: computeComposedJourney(...)
    TA->>TA: adaptCatalogsToWeather()
    TA->>M: getCurrentWeather(city)
    activate M
    M->>OWM: GET /data/2.5/weather?q=city&appid=...
    alt réponse HTTP 200
        OWM-->>M: JSON (main, wind, weather[0])
        M->>M: parseWeatherData(json)
        M-->>TA: WeatherData
    else erreur HTTP / ville inconnue / pas de réseau
        OWM-->>M: erreur HTTP ou exception réseau
        M-->>TA: null
    end
    deactivate M

    alt weather != null
        TA->>CR: applyToCatalog(catalogs, weather)
        activate CR
        alt pluie ou vent très fort
            CR->>Cat: removeIf(moyen == "bike")
        else vent fort (sans être très fort)
            CR->>Cat: adjustIf(moyen == "bike", durée *= 1.5)
        end
        opt neige
            CR->>Cat: adjustIf(moyen == "car", durée *= 1.5, coût *= 1.2)
        end
        CR-->>TA: résumé textuel de l'adaptation (ou null)
        deactivate CR
        TA->>TA: println(résumé)
    else weather == null
        TA->>TA: println("météo indisponible, pas d'adaptation")
    end
```

**Figure 3.** Appelé à chaque recherche, avant `findIndirectJourney`, pour adapter le catalogue agrégé à la météo de la ville de l'utilisateur.

Fig. 4Appel au LLM — demande en langage naturel

```mermaid
sequenceDiagram
    actor Utilisateur
    participant GUI as TravellerGui
    participant TA as TravellerAgent
    participant P as TravelRequestParser
    participant C as OllamaClient
    participant Ollama as Serveur Ollama (localhost:11434)

    Utilisateur->>GUI: saisit une demande en langage naturel + clique "Buy"
    GUI->>TA: postGuiEvent(BUY_TRAVEL, ..., texteLibre)
    TA->>TA: onGuiEvent() : texteLibre non vide
    TA->>P: parse(texteLibre)
    activate P
    P->>C: pickAvailableModel() (une seule fois, mis en cache)
    C->>Ollama: GET /api/tags
    Ollama-->>C: liste des modèles installés
    C-->>P: nom du modèle (ou valeur par défaut)

    P->>C: generate(model, prompt JSON strict)
    C->>Ollama: POST /api/generate {model, prompt, stream:false}
    alt serveur Ollama disponible
        Ollama-->>C: {"response": "...json..."}
        C-->>P: texte généré
        P->>P: extrait et parse le JSON {from, to, departureTime, preference}
        alt JSON valide (zones a..f, critère connu)
            P-->>TA: TravelRequest
            TA->>TA: remplace la demande du formulaire par celle du LLM
        else JSON invalide / champs incohérents
            P-->>TA: null
            TA->>TA: conserve la demande saisie dans le formulaire
        end
    else serveur injoignable / timeout
        Ollama-->>C: (pas de réponse)
        C-->>P: null
        P-->>TA: null
        TA->>TA: conserve la demande saisie dans le formulaire
    end
    deactivate P

    TA->>TA: addBehaviour(ContractNetAchat(...)) — voir figure 2
```

**Figure 4.** Les deux branches de repli (JSON invalide, serveur injoignable) laissent la demande du formulaire inchangée : l'utilisateur n'est jamais bloqué par une indisponibilité du LLM.
