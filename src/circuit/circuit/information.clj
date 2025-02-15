(ns circuit.information
  (:require [circuit.protocol :as protocol]))

(defrecord Contradiction [_reason _data]
  protocol/IContradiction
  (reason [_] _reason)
  (explain [_]
    {:contradiction true
     :reason _reason
     :info   _data}))

(defn ->contradiction [reason data]
  (with-meta
    (->Contradiction reason data)
    {:contradiction true}))

(defn throw-contradiction [contradiction]
  (throw
   (ex-info
    (str "Contradiction: " (protocol/reason contradiction))
    (protocol/explain contradiction))))

(defn throw-contradiction-exception [exception _data]
  (throw
   (ex-info
    (str "Contradiction: " (.getMessage exception))
    (protocol/explain
     (->contradiction
      (.getMessage exception)
      {:coordinate _data
       :exception (or (ex-data exception) exception)})))))

(defn merge-bit [content increment]
  (cond
    (protocol/nothing? content) increment
    (protocol/nothing? increment) content
    (= increment content) content
    :else (->contradiction (str content " != " increment) {})))

(defn generic-merge-bit [content increment]
  (cond
    ;; --- partial information dispatch first
    (protocol/partial-information? content)
    (protocol/merge-information content increment)

    (protocol/partial-information? increment)
    (protocol/merge-information increment content)

    :else (merge-bit content increment)))