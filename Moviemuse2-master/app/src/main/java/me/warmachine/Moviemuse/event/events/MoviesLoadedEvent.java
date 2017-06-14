package me.warmachine.Moviemuse.event.events;

import java.util.List;

import me.warmachine.Moviemuse.entity.Movie;
import me.warmachine.Moviemuse.entity.Result;

public final class MoviesLoadedEvent {

    public final List<Movie> movies;
    public final Result.SortCriteria sortCriteria;

    public MoviesLoadedEvent(List<Movie> movies, Result.SortCriteria sortCriteria) {
        this.movies = movies;
        this.sortCriteria = sortCriteria;
    }

}
