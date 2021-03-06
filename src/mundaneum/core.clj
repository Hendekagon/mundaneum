(ns mundaneum.core
  (:require [mundaneum.query    :refer [describe entity label property query]]
            [backtick           :refer [template]]
            [clj-time.format    :as    tf]))

;; To understand what's happening here, it would be a good idea to
;; read through this document:

;; https://m.wikidata.org/wiki/Wikidata:SPARQL_query_service/queries

;; The first challenge when using Wikidata is finding the right IDs
;; for the properties and entities you must use to phrase your
;; question properly. We have functions to help:

(entity "U2")
;;=> "Q396"

;; Now we know the ID for U2... or do we? Which U2 is it, really?

(describe (entity "U2"))
;; "Irish alternative rock band"

;; No, that's not the one I wanted! Happily, we can refine the request
;; by adding extra criteria:

(describe (entity "U2" :part-of (entity "Berlin U-Bahn")))
;; -> "underground line in Berlin"

;; We also have functions that turn keywords into property values:
(property :instance-of)
;;=> "P31"

;; ... but we use it indirectly through a set of helper functions
;; named for Wikidata namespaces, like wdt, p, ps and pq. The link
;; above will help you understand which one of these you might want
;; for a given query, but it's most often wdt.

;; All parts of countries, ignoring Canada (sorry, Canada)
(query
 '[:select ?biggerLabel ?smallerLabel
   :where [[?bigger (wdt :instance-of) (entity "country")]
           [?bigger (wdt :contains-administrative-territorial-entity) ?smaller]
           :filter [?bigger != (entity "Canada")]]
   :limit 10])
;;=>
;; #{{:biggerLabel "Norway", :smallerLabel "Østfold"}
;;   {:biggerLabel "Japan", :smallerLabel "Nara Prefecture"}
;;   {:biggerLabel "Japan", :smallerLabel "Wakayama Prefecture"}
;;   {:biggerLabel "Ireland", :smallerLabel "County Wicklow"}
;;   ...
;;   }

;; the only people to win both an academy award and a nobel prize
;;
;; (note the _, which translates to SPARQL's ; which means "use the
;; same subject as before")
(query
 '[:select :distinct ?pLabel
   :where [[?p (wdt :award-received) / (wdt :instance-of) * (entity "Nobel Prize")
            _  (wdt :award-received) / (wdt :instance-of) * (entity "Academy Awards")]]])
;;=> #{{:pLabel "Bob Dylan"} {:pLabel "George Bernard Shaw"}}

;; notable murders of the ancient world, with date and location
(query
 '[:select ?killedLabel ?killerLabel ?locationLabel ?when
   :where [[?killed (wdt :killed-by) ?killer]
           [?killed (wdt :date-of-death) ?when]
           [?killed (wdt :place-of-death) ?location]]
   :order-by (asc ?when)
   :limit 5])
;;=>
;; [{:when #object[org.joda.time.DateTime 0x6c3de49 "-0513-01-01T00:00:00.000Z"],
;;   :killedLabel "Hipparchus",
;;   :killerLabel "Harmodius and Aristogeiton",
;;   :locationLabel "Athens"}
;;  {:when #object[org.joda.time.DateTime 0x5d440dce "-0490-01-01T00:00:00.000Z"],
;;   :killedLabel "Eurybates",
;;   :killerLabel "Sophanes",
;;   :locationLabel "Aegina"}
;;  {:when #object[org.joda.time.DateTime 0x6c6eaddc "-0479-01-01T00:00:00.000Z"],
;;   :killedLabel "Ephialtes of Trachis",
;;   :killerLabel "Athénade", :locationLabel "Thessaly"}]

;; Discoveries/inventions grouped by person on the clojure side,
;; uncomment the second part of the :where clause to specify only
;; female inventor/discovers
(->> (query
      '[:select ?thingLabel ?whomLabel
        :where [[?thing (wdt :discoverer-or-inventor) ?whom
;;                 _ (wdt :sex-or-gender) (entity "female")
                 ]]
        :limit 100])
 (group-by :whomLabel)
 (reduce #(assoc %1 (first %2) (mapv :thingLabel (second %2))) {}))
;;=>
;;  "Enrico Fermi" ["Monte Carlo method" "Fermi resonance" "Metropolis–Hastings algorithm" "Fermi–Walker transport"],
;;  "Napoleon" ["Napoleon's theorem"],
;;  "Abd al-Rahman al-Sufi" ["Brocchi's Cluster"],
;;  "Noam Chomsky" ["Language acquisition device"],
;; ...

;; eye color popularity, grouping and counting as part of the query
(query
 '[:select ?eyeColorLabel (count ?person :as ?count)
   :where [[?person (wdt :eye-color) ?eyeColor] ]
   :group-by ?eyeColorLabel
   :order-by (desc ?count)])
;;=>
;; [{:eyeColorLabel "blue", :count "342"}
;;  {:eyeColorLabel "brown", :count "303"}
;;  {:eyeColorLabel "green", :count "217"}
;;  {:eyeColorLabel "black", :count "145"}
;; ...

;; airports within 20km of Paris, use "around" service
(query
 '[:select ?place ?placeLabel ?location
   :where [[(entity "Paris") (wdt :coordinate-location) ?parisLoc]
           [?place (wdt :instance-of) (entity "airport")]
           :service wikibase:around [[?place (wdt :coordinate-location) ?location]
                                     [bd:serviceParam wikibase:center ?parisLoc]
                                     [bd:serviceParam wikibase:radius "20"]]]])
;; [{:place "Q1894366", :location "Point(2.191667 48.774167)", :placeLabel "Villacoublay Air Base"}
;;  {:place "Q1894366", :location "Point(2.19972222 48.77305556)", :placeLabel "Villacoublay Air Base"}
;; ...

;; U1 stations in Berlin w/ geo coords
(query (template
        [:select ?stationLabel ?coord
         :where [[?station (wdt :connecting-line) (entity "U1" :part-of ~(entity "Berlin U-Bahn"))
                  _ (wdt :coordinate-location) ?coord]]]))
;;=>
;; #{{:coord "Point(13.382777777 52.499166666)",
;;    :stationLabel "Möckernbrücke"}
;;   {:coord "Point(13.343055555 52.501944444)",
;;    :stationLabel "Wittenbergplatz"}
;;   {:coord "Point(13.332777777 52.504166666)",
;;    :stationLabel "Kurfürstendamm"}
;; ...

;; born in Scotland or territories thereof
(query
 '[:select ?itemLabel ?pobLabel
   :where [:union [[?item (wdt :place-of-birth) (entity "Scotland")]
                   [[?item (wdt :place-of-birth) ?pob]
                    [?pob (wdt :located-in-the-administrative-territorial-entity) * (entity "Scotland")]]]]
   :limit 10])
;; [{:itemLabel "Colin Maclaurin"}
;;  {:itemLabel "Scrooge McDuck"}
;;  {:itemLabel "Patrick Jenkin"}
;;  {:itemLabel "Duncan I of Scotland"}]

;; A somewhat complicated question about presidential precedence,
;; which involves: querying against property statements and property
;; qualifiers, plus the use of _ (a stand-in for SPARQL's semicolon
;; operator, which is says "continue this expression using the same
;; entity"):
(query
 '[:select ?prevLabel
   :where [[(entity "Barack Obama") (p :position-held) ?pos]
           [?pos (ps :position-held) (entity "President of the United States of America")
            _ (pq :replaces) ?prev]]])
;;=>#{{:prevLabel "George W. Bush"}}

;; which can be trivially expanded to list all US presidents and their
;; predecessors
(query
 '[:select ?prezLabel ?prevLabel
   :where [[?prez (p :position-held) ?pos]
           [?pos (ps :position-held) (entity "President of the United States of America")
            _ (pq :replaces) ?prev]]])
;;=>#{{:prezLabel "John Tyler", :prevLabel "William Henry Harrison"}
;;    {:prezLabel "Gerald Ford", :prevLabel "Richard Nixon"}
;;    {:prezLabel "John Adams", :prevLabel "George Washington"}
;; ...

;; We can also use triples to find out about analogies in the dataset
(defn make-analogy
  "Return known analogies for the form `a1` is to `a2` as `b1` is to ???"
  [a1 a2 b1]
  (->> (query
        (template [:select ?isto ?analogyLabel
                   :where [[~(symbol (str "wd:" a1)) ?isto ~(symbol (str "wd:" a2))]
                           [~(symbol (str "wd:" b1)) ?isto ?analogy]
                           ;; tightens analogies by requiring that a2/b2 be of the same kind,
                           ;; but loses some interesting loose analogies:
                           ;; [~(symbol (str "wd:" a2)) (wdt :instance-of) ?kind]
                           ;; [?analogy (wdt :instance-of) ?kind]
                           ]]))
       (map #(let [arc (label (:isto %))]
               (str (label a1)
                    " is <" arc "> to "
                    (label a2)
                    " as "
                    (label b1)
                    " is <" arc "> to " (:analogyLabel %))))
       distinct))

(apply make-analogy (map entity ["The Beatles" "rock and roll" "Miles Davis"]))
;;=> ("The Beatles is <genre> to rock and roll as Miles Davis is <genre> to jazz")

(make-analogy (entity "Daft Punk")
              (entity "Paris")
              ;; clarify the jape we mean
              (entity "Jape" :instance-of (entity "band")))
;;=> ("Daft Punk is <location of formation> to Paris as Jape is <location of formation> to Dublin")

(defn releases-since
  "Returns any creative works published since `year`/`month` by any `entities` known to Wikidata."
  [since-year since-month entities]
  (let [ents (map #(if (re-find #"^Q[\d]+" %) % (entity %)) entities)]
    (query
     (template [:select ?workLabel ?creatorLabel ?role ?date
                :where [[?work ?role ?creator _ (wdt :publication-date) ?date]
                        :union ~(mapv #(vector '?work '?role (symbol (str "wd:" %))) ents)
                        :filter ((year ?date) >= ~since-year)
                        :filter ((month ?date) >= ~since-month)]
                :order-by (asc ?date)]))))

(defn humanize-releases
  "Make the data presentable."
  [releases]
  (->> (group-by :workLabel releases)
       (map (fn [[work roles]]
              [(:date (first roles))
               work
               (str (:creatorLabel (first roles))
                    " ("
                    (->> (map :role roles)
                         (map label)
                         distinct
                         (interpose "/")
                         (apply str))
                    ")")]))
       (sort-by first)
       (map #(conj (rest %) (tf/unparse (tf/formatter "d MMMM, yyyy") (first %))))))

(->> (releases-since 2016 1 ; year and month
                   ["Kelly Link" "Stromae" "Guillermo del Toro" "Hayao Miyazaki" "Lydia Davis"
                    "Werner Herzog" "Björk" "George Saunders" "Feist" "Andrew Bird" "Sofia Coppola"])
     humanize-releases)
;; (("1 January, 2016" "Salt and Fire" "Werner Herzog (director/screenwriter/cast member)")
;;  ("1 January, 2016" "Lo & Behold, Reveries of the Connected World" "Werner Herzog (director/screenwriter)")
;;  ("1 January, 2016" "Into the Inferno" "Werner Herzog (director)")
;;  ("1 April, 2016" "Are You Serious" "Andrew Bird (performer)")
;;  ("1 January, 2017" "Boro The Caterpillar" "Hayao Miyazaki (director)")
;;  ("1 January, 2017" "The Shape of Water" "Guillermo del Toro (director)")
;;  ("14 February, 2017" "Lincoln in the Bardo" "George Saunders (author)")
;;  ("14 April, 2017" "Queen of the Desert" "Werner Herzog (director/screenwriter)")
;;  ("23 June, 2017" "The Beguiled" "Sofia Coppola (director/screenwriter/producer)")
;;  ("15 September, 2017" "The Gate" "Björk (performer)")
;;  ("1 November, 2017" "Utopia" "Björk (performer)")
;;  ("15 November, 2017" "Blissing Me (song)" "Björk (performer)")
;;  ("1 January, 2018" "Pacific Rim Uprising" "Guillermo del Toro (screenwriter/producer)"))

;; (query
;;  '[:select ?awdLabel ?countryLabel  (count ?p :as ?count)
;;    :where [[?p (wdt :award-received) ?awd
;;             _  (wdt :place-of-birth) ?birthplace]
;;            [?awd (wdt :instance-of) (entity "Nobel Prize")]
;;            [?birthplace (wdt :country) ?country]]
;;    :group-by ?awdLabel ?countryLabel
;;    :order-by (desc ?count)])
