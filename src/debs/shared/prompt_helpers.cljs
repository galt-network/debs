(ns debs.shared.prompt-helpers)

(def tone-definitions
  {:neutral "You must use neutral tone in your response."
   :witty "Use witty, playful tone in your response."
   :ironic "Use ironic tone in your response."
   :confrontational "Use confrontational, challenging tone in your response."})

(defn tone->prompt
  [option]
  (get tone-definitions (keyword option)))

(def length-definitions
  {:tweet "Response must be concise with length ideally under 280 characters, to fit in a Tweet."
   :double-tweet "Response must be concise with length ideally under 560 characters (double length of a Tweet)."
   :three-paragraphs "Response must consist of 3 paragraphs."
   :detailed "Response length must be between 3 and 5 paragraphs."})

(defn length->prompt
  [option]
  (get length-definitions (keyword option)))

(defn prompt-options->prompt
  [options]
  (str
    (tone->prompt (get options :response-tone))
    "\n"
    (length->prompt (get options :response-length))))
