package io.github.newsticker;

/** One news feed: an id name, the URL to fetch and the source it comes from. */
final class Feed {
    final String name;
    final String url;
    final String source;

    Feed(String name, String url, String source) {
        this.name = name;
        this.url = url;
        this.source = source;
    }
}
