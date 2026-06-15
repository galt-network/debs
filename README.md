# DeBS

A tool that helps to unveil and refute collectivist fallacies and lies according to libertarian principles (NAP, right to life, liberty and property).

## Development

1. Start clojure REPL
```bash
npx shadow-cljs clj-repl
```

2. Start watching the code
```clojure
(doseq [build-name [:content-script :service-worker :side-panel :popup :pwa :server]] (shadow/watch build-name))
```
## Notes

Get individual tweets: https://docs.twitterapi.io/api-reference/endpoint/get_tweet_by_ids
