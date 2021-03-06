(ns dtm.util
  (:require [datomic.api :as d]
            [config.dtm :as config]))

(defn concatv [& lists]
  (-> (apply concat lists)
      vec))

(defn get-conn []
  (d/connect config/uri))

(defn get-db []
  (d/db (get-conn)))

(defn str->uuid [s]
  (java.util.UUID/fromString s))

(defn uuid []
  (java.util.UUID/randomUUID))

(defn concat-lists [collection-of-lists]
  (vec (reduce concat [] collection-of-lists)))

(defn get-eid
  "Gives the `eid` of entity whose attribute `attr` has value `value`.
  Assumes that `attr` is unique otherwise gives the first `eid` from the set
  returned by datomic."
  [attr val db]
  (let [q `[:find ?eid
            :where [?eid ~attr ~val]]]
    (ffirst (d/q q db))))

;; todo - gives all values(of all writes), not just the last written ones. see in what sense its being used everywhere(what is the intended behaviour) and change the implementation or make another function.
(defn get-all-vals
  "Gets all the values of attribute `attr` in all entities."
  [attr db]
  (let [q `[:find ?val
            :where
            [?eid ~attr]
            [?eid ~attr ?val]]]
    (concat-lists (d/q q db))))

(defn get-details
  "Gets the details of any entity `e`; `eid`; e having value of `attr` as `val`."
  ([e]
   (when e
     (d/touch e)))

  ([eid db]
   (when (and eid db)
     (d/touch (d/entity db eid))))

  ([attr value db]
   (let [eid (get-eid attr value db)]
     (get-details eid db))))

(defn get-attr
  "Gets a particular attribute `attr-to-get` of an entity."
  [attr-to-get & get-details-params]
  (attr-to-get (apply get-details get-details-params)))

(defn diff [manifest all]
  (let [not-contains?    (complement contains?)

        manifest-set     (set manifest)
        all-set          (set all)

        not-in-manifest? (partial not-contains? manifest-set)
        not-in-all?      (partial not-contains? all-set)

        insert (filterv not-in-manifest? all)
        delete (filterv not-in-all?  manifest)]
    {:insert insert
     :delete delete}))

(defn filter-nil [cmap]
  (into {} (filter (comp not nil? second) cmap)))


;;; ================================user========================================


(defn get-org-unit-eid
  "Gets the eid of the orgUnit of any user having username as `username`."
  [username db]
  (let [q  `[:find ?e-org-unit
             :where
             [?eu         :user/username ~username]
             [?e-org-unit :vertical/users ?eu]]]
    (ffirst (d/q q db))))


;;; ================================tasks=======================================


(defn parent [task-eid db]
  (:db/id (get-attr :task/parent task-eid db)))

(defn sibling [task-eid db]
  (:db/id (get-attr :task/sibling task-eid db)))

(defn first-child [task-eid db]
  (:db/id (get-attr :task/first-child task-eid db)))

(defn relatives [task-id db]
  (let [task-eid (get-eid :task/id task-id db)]
    {:parent      (get-attr :task/id (parent task-eid db) db)
     :sibling     (get-attr :task/id (sibling task-eid db) db)
     :first-child (get-attr :task/id (first-child task-eid db) db)}))

(defn neighbours [task-id db]
  (-> (relatives task-id db)
      (filter-nil)
      vals
      vec))

(defn connected-tasks
  ([task-id db]
   (connected-tasks [] [task-id] db))
  ([visited unvisited db]
   (if (empty? unvisited)
     visited
     (let [to-visit        (first unvisited)
           new-neighbours  (neighbours to-visit db)
           to-insert       (-> (concatv visited unvisited)
                               (diff new-neighbours)
                               :insert)
           new-visited     (concatv visited [to-visit])
           new-unvisited   (concatv (rest unvisited) to-insert)]
       (connected-tasks new-visited new-unvisited db)))))

(defn root [task-eid db]
  (if-let [p (parent task-eid db)]
    (root p db)
    (if-let [s (sibling task-eid db)]
      (root s db)
      task-eid)))

(defn get-root-eid [task-id db]
  (let [task-eid (get-eid :task/id task-id db)]
    (root task-eid db)))

(defn get-activity-eid [task-id db]
  (let [root-eid     (get-root-eid task-id db)
        activity-eid (get-eid :activity/root root-eid db)]
    activity-eid))

(defn get-project-eid [task-id db]
  (let [activity-eid (get-activity-eid task-id db)
        project-eid  (get-eid :project/activities activity-eid db)]
    project-eid))

(defn get-tasks-ids
  ([status username db]
   (let [q `[:find ?tid
             :where
             [?eu :user/username ~username]
             [?em :assignment-measurement/value ?eu]
             [?et :task/assigned-to ?em]
             [?et :task/status ~status]
             [?et :task/id ?tid]]]
     (concat-lists (d/q q db))))

  ([status username type db]
   (let [q `[:find ?tid
             :where
             [?eu :user/username ~username]
             [?em :assignment-measurement/value ?eu]
             [?et :task/assigned-to ?em]
             [?et :task/status ~status]
             [?et :task/type ~type]
             [?et :task/id ?tid]]]
     (concat-lists (d/q q db)))))

(defn get-pending-tasks-ids [username db]
  (get-tasks-ids :task.status/pending username db))

(defn get-completed-tasks-ids [username db]
  (get-tasks-ids :task.status/completed username :task.type/measurement db))

(defn get-assigned-completed-tasks-ids [username db]
  (get-tasks-ids :task.status/completed username :task.type/assignment db))

(defn get-assigned-pending-tasks-ids [username db]
  (get-tasks-ids :task.status/assigned username db))

(defn get-task-assigned-to-eid [assignment-measurement-id db]
  (get-eid :task/assigned-to assignment-measurement-id db))

(defn full-name [user-details]
  (let [{:keys
         [firstName
          lastName]} user-details]
    (str firstName " " lastName)))

(defn org-unit-name [user-details]
  (let [db (get-db)]
    (-> user-details
        :username
        (get-org-unit-eid db)
        (get-details db)
        :vertical/name)))

(defn assignee-details [assignment-measurement-entity]
  (-> assignment-measurement-entity
      get-details
      :assignment-measurement/value
      get-details))

(defn root? [task-id db]
  (let [task-eid (get-eid :task/id task-id db)
        root-eid (get-root-eid task-id db)]
    (= task-eid root-eid)))

(defn get-assigner-eid [task-id db]
  (if (root? task-id db)
    (let [activity-eid (get-activity-eid task-id db)
          q           `[:find ?e-owner
                        :where
                        [~activity-eid :activity/owner ?e-owner]]
          assigner-eid (ffirst (d/q q db))]
      assigner-eid)

    (let [q `[:find ?e-assigner
              :where
              [?e-assigned-t :task/id ~task-id]
              [?e-assigned-t :task/assigned-to ?e-assignment-m]
              [?e-m-template :measurement-template/measurement ?e-assignment-m]
              [?e-assigner-t :task/measurement-templates ?e-m-template]
              [?e-assigner-t :task/assigned-to ?e-assigner-t-assignment-m]
              [?e-assigner-t-assignment-m :assignment-measurement/value ?e-assigner]]]
      (ffirst (d/q q db)))))


;;; ================================templates/activities========================


(defn get-activity-templates-ids [db]
  (get-all-vals :activity-template/id db))


;;; ================================PUT tasks===================================

(defn transact [tx]
  @(d/transact (get-conn) tx))


;;; ==============================ordering datomic set==========================


(defn add-position-recur
  ([maps attr pos]
   (let [first-map (first maps)]
     (if first-map
       (let [appended-first-map (assoc first-map attr pos)]
         (into [appended-first-map] (add-position-recur (rest maps)
                                                        attr
                                                        (inc pos))))
       []))))

(defn add-position
  ([maps attr]
   (add-position-recur maps attr 1))

  ([maps]
   (add-position maps :position)))

(defn sort-by-position
  ([maps attr]
   (let [cmaps       (map (partial into {}) maps)
         sorted-maps (vec (sort-by attr cmaps))]
     (map #(dissoc % attr) sorted-maps)))
  ([maps]
   (sort-by-position maps :position)))

(defn get-vertical-id [state vertical db]
  (let [q `[:find ?vid
            :where
            [?s :state/name ~state]
            [?s :state/verticals ?v]
            [?v :vertical/name ~vertical]
            [?v :vertical/id ?vid]]]
    (ffirst (d/q q db))))

(defn get-state-id [state db]
  (let [q `[:find ?sid
            :where
            [?s :state/name ~state]
            [?s :state/id ?sid]]]
    (ffirst (d/q q db))))

(defn get-project-id [project db]
  (let [q `[:find ?pid
            :where
            [?p :project/name ~project]
            [?p :project/id ?pid]]]
    (ffirst (d/q q db))))


(defn retract-entity [attribute value]
  (transact [[:db.fn/retractEntity [attribute value]]]))
