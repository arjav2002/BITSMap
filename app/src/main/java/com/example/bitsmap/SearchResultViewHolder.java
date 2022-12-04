package com.example.bitsmap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

// describes each item in search results recycler view.
public class SearchResultViewHolder extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final MainActivity mainActivity;
    private final ArrayList<SearchResult> searchResults;

    public SearchResultViewHolder(MainActivity mainActivity, ArrayList<SearchResult> searchResults, RecyclerView recyclerView) {
        this.mainActivity = mainActivity;
        this.searchResults = searchResults;

        recyclerView.setAdapter(this);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SearchResultHolder(LayoutInflater.from(mainActivity).inflate(R.layout.search_result, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchResultHolder searchResultHolder = (SearchResultHolder) holder;
        SearchResult searchResult = searchResults.get(position);
        MapNode node = searchResult.getMapNode();

        searchResultHolder.resultButton.setText(searchResult.getHighlightedInfra().getName() + ", Floor: " + (int)node.getPosition().getZ());
        searchResultHolder.resultButton.setOnClickListener((View view) -> {
            if(mainActivity.isSelectingSourceLocation()) {
                mainActivity.setStartInfra(searchResult.getHighlightedInfra());
            }
            else if(mainActivity.isSelectingDestinationLocation()) {
                mainActivity.setDestinationInfra(searchResult.getHighlightedInfra());
            }
            else {
                mainActivity.searchResult(searchResult.getHighlightedInfra());
            }
        });

        searchResultHolder.resultButton.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    private static class SearchResultHolder extends RecyclerView.ViewHolder {
        private final Button resultButton;

        public SearchResultHolder(@NonNull View itemView) {
            super(itemView);

            resultButton = itemView.findViewById(R.id.result_button);
        }
    }
}
