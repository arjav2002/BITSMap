package com.example.bitsmap;

public class SearchResult {

    private Infra highlightedInfra;
    private MapNode mapNode;

    public SearchResult(Infra highlightedInfra, MapNode mapNode) {
        this.highlightedInfra = highlightedInfra;
        this.mapNode = mapNode;
    }

    public Infra getHighlightedInfra() { return highlightedInfra; }
    public MapNode getMapNode() { return mapNode; }
}
