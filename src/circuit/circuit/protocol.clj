(ns circuit.protocol)

(defn safe-meta [thing] (try (meta thing) (catch Exception _e false)))
(def nothing 'nothing)
(defn nothing? [x] (= x nothing))
(defn something? [x] (not (nothing? x)))
(defn contradiction? [x] (-> x safe-meta :contradiction))

(defprotocol IAbstractCircuit
  "A protocol for the topology of the circuit."
  (order   [this]
    "Return ordered set of neurons tag.")
  (structure [this]
    "Return structure (hierarchy of the network)."))

(defprotocol IGame
  "A game needs a solution concept and a mechanism, a set of rules (implementations)."
  (solution [abstract-circuit solution-concept]
    "Return an abstract circuit with the solution concept.
     \nSignature
    
    (solution [abstract-circuit variable instantiation])")
  (mechanism [this implementations]
    "Lift symbolic neurons to concrete implementations")
  (learn [this facts] "Move the game foreward from knowledge derived from facts."))

(defprotocol IOrganism
  "A circuit is a complex system made of cells and neurons."
  (neurons [this]
    "Return coll of neurons.")
  (cells   [this]
    "Return coll of cells.")
  (get-neuron [this t]
    "Return neuron from tag.")
  (get-cell [this t]
    "Return cell from tag.")
  (add-cell [this cell]
    "Add cell, does not recalculate structure.")
  (add-neuron [this neuron]
    "Add neuron, does not recalculate structure.")
  (assimilate [this facts]
    "Assimilate external knowledge into cells.")
  (acknowledge [this cell]
    "Acknowledge event from cell."))

(defprotocol ITag
  "A protocol to tag things."
  (tag [this] "Return symbolic tag"))

(defprotocol ISymbolicNeuron
  (arg-cells [this]
    "Return symbolic argument cells")
  (to-cell   [this]
    "Return symbolic output cell")
  (activate  [this circuit]
    "Activate and return event cell")
  ;; WIP subject to change
  (obey      [this circuit]
    "Activate no matter what.")
  (implement [this fn-]
    "Install an implementation on the Symbolic Neuron"))

(defprotocol ISymbolicCell
  (assume  [this bit] [this bit signal]
    "Assume a bit of information")
  (content [this]
    "Reveal content")
  (signals [this]
    "Reveal signals"))

(defprotocol IMerge
  (merge-information [this increment] [this increment signal]
    "Building block for writing partial information s.
     merge bit must be
     - associative 
     - idempotent"))

(defprotocol IContradiction
  (reason [this]
    "Contradiction string reason")
  (explain [this]
    "Contradiction info as data"))

(defn partial-information?
  "Types must define some of their properties in the meta
   
   'partial-information true 
   
   means that the type has a merge operation. "
  [x]
  (get (safe-meta x) 'partial-information))