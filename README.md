# deBS - de-bullshit the socialist/collectivist/statist on social media

A tool that helps to unveil and refute collectivist fallacies and lies according to libertarian principles (NAP, right to life, liberty and property).

# Parts

## Browser extension

## [deBS](https://debs.galt.is) Progressive Web Application

Most convenient to use on mobile phones.
1. Go to [https://debs.galt.is](https://debs.galt.is) and install the PWA by putting the application icon to your screen.
2. Open X mobile application and find a socialist BS tweet
3. Tap _Share_ and pick the deBS application
4. The tweet content will appear in the application where you can ask to deBS it
5. Click _Copy_ to paste and edit the answer in your twitter app OR click _Post_ which returns you to X application and opens reply dialog with the answer pre-filled

![Logo](docs/images/debs-pwa.png)

## Development

1. Start clojure REPL
```bash
npx shadow-cljs clj-repl
```

2. Start watching the code
```clojure
(doseq [build-name [:content-script :service-worker :side-panel :popup :pwa :server]] (shadow/watch build-name))
```
