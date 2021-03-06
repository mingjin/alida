;; src/alida/lucene.clj: Apache Lucene search-related functions
;;
;; Copyright 2012, F.M. de Waard & Vixu.com <fmw@vixu.com>.
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;; 
;; http://www.apache.org/licenses/LICENSE-2.0
;; 
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns alida.lucene
  (:require [alida.util :as util])
  (:import [java.io File]
           [org.apache.lucene.document
            Document
            Field
            FieldType
            FieldType$NumericType
            DoubleField
            FloatField
            IntField
            LongField]
           [org.apache.lucene.analysis.standard StandardAnalyzer]
           [org.apache.lucene.store Directory NIOFSDirectory RAMDirectory]
           [org.apache.lucene.index
            IndexWriter
            IndexWriterConfig
            IndexWriterConfig$OpenMode
            IndexReader
            Term
            IndexNotFoundException]
           [org.apache.lucene.search
            ScoreDoc
            NumericRangeQuery
            TermQuery
            BooleanQuery
            BooleanClause$Occur
            QueryWrapperFilter
            IndexSearcher]
           [org.apache.lucene.queryparser.flexible.standard
            StandardQueryParser]
           [org.apache.lucene.queryparser.flexible.standard.parser
            ParseException
            TokenMgrError]
           [org.apache.lucene.queryparser.flexible.core
            QueryNodeParseException]
           [org.apache.lucene.util Version]))

(defn #^FieldType create-field-type [data-type & options]
  "Creates a Lucene FieldType object given a data-type
   (either :string, :double, :float, :int or :long) and options
   (:indexed, :tokenized, :store).

   tokenized:
   tokenizes the value (breaks it up) for fulltext search.

   stored:
   makes the field retrievable from the index, instead of just
   processable at search time.

   :indexed:
   makes the field searchable and filterable.

   Nota bene: a field needs to be either :stored or :indexed.
   Also, it doesn't make sense to tokenize numeric values."
  (let [field-type (FieldType.)
        types {:double FieldType$NumericType/DOUBLE
               :float FieldType$NumericType/FLOAT
               :int FieldType$NumericType/INT
               :long FieldType$NumericType/LONG}]
    ;; note - if only used for sorting (and not querying/filtering)
    ;; setting precisionStep to Integer.MAX_VALUE is more efficient
    ;; for numeric fields (this will minimize disk space consumed).
    (when (contains? types data-type)
      (.setNumericType field-type (types data-type)))
    
    (doto field-type
      (.setIndexed (not (nil? (some #{:indexed} options))))
      (.setStored (not (nil? (some #{:stored} options))))
      (.setTokenized (not (nil? (some #{:tokenized} options))))
      (.freeze))))

(defmulti #^Field create-field
  "Creates a field with the given name and value that has a
   FieldType created using the provided options."
  (fn [name value & options]
    (class value)))

(defmethod create-field java.lang.String [name value & options]
  (Field. name
          (or value "")
          (apply (partial create-field-type :string) options)))

(defmethod create-field java.lang.Long [name value & options]
  (LongField. name
              (or value 0)
              (apply (partial create-field-type :long) options)))

(defmethod create-field java.lang.Integer [name value & options]
  (IntField. name
             (or value (int 0))
             (apply (partial create-field-type :int)  options)))

(defmethod create-field java.lang.Float [name value & options]
  (FloatField. name
               (or value (float 0.0))
               (apply (partial create-field-type :float) options)))

(defmethod create-field java.lang.Double [name value & options]
  (DoubleField. name
                (or value 0.0)
                (apply (partial create-field-type :double) options)))

(defn #^LongField create-date-field
  "Creates a Lucene LongField for the provided rfc3339-date-string.."
  [name rfc3339-date-string & options]
  (apply (partial create-field
                  name
                  (util/rfc3339-to-long rfc3339-date-string))
         options))

(defmulti #^Field set-field-value!
  "Sets the value of the Lucene Field object to the provided value.
   The Lucene documentation recommends this approach over creating
   new fields for every document (for performance reasons)."
  (fn [field value]
    (class value)))

(defmethod set-field-value! java.lang.String [field value]
  (doto field
    (.setStringValue value)))

(defmethod set-field-value! java.lang.Long [field value]
  (doto field
    (.setLongValue value)))

(defmethod set-field-value! java.lang.Integer [field value]
  (doto field
    (.setIntValue value)))

(defmethod set-field-value! java.lang.Float [field value]
  (doto field
    (.setFloatValue value)))

(defmethod set-field-value! java.lang.Double [field value]
  (doto field
    (.setDoubleValue value)))

(defn #^Document create-document-
  "Takes two hash maps, fields-map with a Lucene Field instance for
   every key, and values-map with the same keys mapped to the desired
   values for the document. Creates a Lucene Document object with the
   Field instances from the fields-map set to the values provided in
   the value-map. This fn shouldn't be called directly, but from the
   add-documents-to-index! fn instead, because the Field instances are
   reused. Since they are mutable they will be updated to the value
   for the last document, unless written to the index immediately."
  [fields-map values-map]
  (let [doc (Document.)]
    (doseq [[k field] fields-map]
      (.add doc (set-field-value! field (values-map k))))
    doc))

(defn document-to-map
  "Converts a Lucene Document to a Clojure map."
  [doc]
  (apply merge
         (map (fn [field]
                {(keyword (.name field))
                 (or (.numericValue field) (.stringValue field))})
              (.getFields doc))))

(defn #^StandardAnalyzer create-analyzer
  "Creates a StandardAnalyzer that tokenizes fulltext fields."
  []
  (StandardAnalyzer. (. Version LUCENE_40)))

(defn create-directory
  "Create a directory with either :RAM or a directory as the path argument."
  [path]
  (if (= path :RAM)
    (RAMDirectory.)
    (NIOFSDirectory. (File. path))))

(defn #^IndexReader create-index-reader
  "Creates IndexReader for the specified directory, but returns nil if
   the provided directory exists but doesn't contain a valid index."
  [#^Directory directory]
  (try
    (. IndexReader open directory)
    (catch IndexNotFoundException e
      nil)))

(defn #^IndexWriter create-index-writer
  "Creates an IndexWriter with the provided analyzer and directory.
   The mode has three options: :create, :append or :create-or-append."
  [analyzer directory mode]
  (let [config (IndexWriterConfig. (Version/LUCENE_40) analyzer)
        open-modes {:create
                    IndexWriterConfig$OpenMode/CREATE
                    :append
                    IndexWriterConfig$OpenMode/APPEND
                    :create-or-append
                    IndexWriterConfig$OpenMode/CREATE_OR_APPEND}]

    (doto config
      ;;(.setRAMBufferSizeMB 49)
      (.setOpenMode (open-modes mode)))
    
    (IndexWriter. directory config)))

(defn add-documents-to-index!
  "Converts the provided document-value-maps to Lucene Document
   objects using the fields from the given fields-map and writes them
   to an index through the provided writer.  Wrap this in with-open to
   close the writer and flush the changes."
  [writer fields-map document-value-maps]
  (doseq [doc document-value-maps]
    (.addDocument writer (create-document- fields-map doc))))

(comment
  (defn update-document-in-index!
    "Updates a document by first deleting the document(s) matching the
     provided term-value in the term-field and then adding the new
     document. The delete and then add are atomic as seen by a reader
     on the same index (flush may happen only after the add). Uses the
     provided fields-map with Lucene Field instances for each field
     and regular Clojure doc map which contains the values to
     construct the new Lucene Document object. Optionally accepts an
     analyzer (using (create-analyzer) by default). Wrap this in a
     with-open that constructs the writer, because changes are flushed
     when the writer is closed."
    [writer term-field term-value fields-map doc & [analyzer]]
    (.updateDocument writer
                     (Term. term-field term-value)
                     (create-document- fields-map doc)
                     (or analyzer (create-analyzer)))))

(defn #^TermQuery create-term-query
  "Creates a Lucene TermQuery for the provided field and value."
  [field value]
  (TermQuery. (Term. field value)))

(defn valid-range-of-type? [min max type]
  "Checks if min is lower than max and both are of the given Java class."
  (and (= (class min) (class max) type)
       (>= max min)))

(defmulti #^NumericRangeQuery create-numeric-range-query
  "Creates a Lucene NumericRangeQuery between the min and max value."
  (fn [field-name min max]
    (class min)))

(defmethod create-numeric-range-query java.lang.Long [field-name min max]
  (when (valid-range-of-type? min max java.lang.Long)
    (NumericRangeQuery/newLongRange field-name min max true true)))

(defmethod create-numeric-range-query java.lang.Float [field-name min max]
  (when (valid-range-of-type? min max java.lang.Float)
    (NumericRangeQuery/newFloatRange field-name min max true true)))

(defmethod create-numeric-range-query java.lang.Double [field-name min max]
  (when (valid-range-of-type? min max java.lang.Double)
    (NumericRangeQuery/newDoubleRange field-name min max true true)))

(defmethod create-numeric-range-query java.lang.Integer [field-name min max]
  (when (valid-range-of-type? min max java.lang.Integer)
    (NumericRangeQuery/newIntRange field-name min max true true)))

(defn #^NumericRangeQuery create-date-range-query
  "Creates a NumericRangeQuery for field-name using start and end date
   string arguments."
  [field-name start-date-rfc3339 end-date-rfc3339]
  (create-numeric-range-query field-name
                              (util/rfc3339-to-long start-date-rfc3339)
                              (util/rfc3339-to-long end-date-rfc3339)))

(defn #^BooleanQuery create-boolean-query
  "Creates a Lucene BooleanQuery for the provided pairs of queries
  and occur clauses (i.e. :must, :must-not, :should)."
  [& pairs]
  (when (even? (count pairs))
    (let [bq (BooleanQuery.)
          occur-reqs {:must BooleanClause$Occur/MUST
                      :must-not BooleanClause$Occur/MUST_NOT
                      :should BooleanClause$Occur/SHOULD}]
      (doseq [[query clause] (partition 2 pairs)]
        (.add bq query (occur-reqs clause)))
      
      (when (pos? (alength (.getClauses bq)))
        bq))))

(defn #^QueryWrapperFilter create-query-wrapper-filter
  "Creates a Lucene QueryWrapperFilter (i.e. a Query made into a Filter)."
  [query]
  (when query
    (QueryWrapperFilter. query)))

(defn #^Document get-doc
  "Reads the document with the provided doc-id from the index."
  [reader doc-id]
  (.document reader doc-id))

(defn get-docs
  "Returns a sequence of the individual documents for a Lucene ScoreDoc[]
   array."
  [reader score-docs]
  (map #(get-doc reader (.doc %)) score-docs))

(defn search
  "Returns search results for the provided query and filter, using
   the fulltext-field as the main field to query against. Results
   are capped by the limit argument. Search is performed using the
   provided reader and analyzer. Also accepts optional after-doc-id
   and after-score arguments for pagination."
  [query
   filter
   fulltext-field
   limit
   reader
   analyzer
   & [after-doc-id after-score]] 
  (if (and reader
           (not-empty query)
           (not-any? #(= (first query) %) #{\*\?}))
    (try
      (let [searcher (IndexSearcher. reader)
            q (.parse (StandardQueryParser. analyzer) query fulltext-field)
            top-docs (if (nil? after-doc-id)
                       (if (nil? filter)
                         (.search searcher q limit)
                         (.search searcher q filter limit))
                       (if (nil? filter)
                         (.searchAfter searcher
                                       (ScoreDoc. after-doc-id
                                                  after-score)
                                       q
                                       limit)
                         (.searchAfter searcher
                                       (ScoreDoc. after-doc-id
                                                  after-score)
                                       q
                                       filter
                                       limit)))]
        {:total-hits (.totalHits top-docs)
         :docs (map (fn [score-doc doc]
                      (assoc (document-to-map doc)
                        :index {:doc-id (.doc score-doc)
                                :score (.score score-doc)}))
                    (.scoreDocs top-docs)
                    (get-docs reader (.scoreDocs top-docs)))})
      (catch ParseException e
        nil)
      (catch TokenMgrError e
        nil)
      (catch QueryNodeParseException e
        nil))
    {:total-hits 0
     :docs nil}))