package me.warmachine.Moviemuse.event.events;

import me.warmachine.Moviemuse.entity.Result;

public final class LoadMovies implements Api {

    public final Result.SortCriteria sortCriteria;

    public LoadMovies(Result.SortCriteria sortCriteria) {
        this.sortCriteria = sortCriteria;
    }

}
