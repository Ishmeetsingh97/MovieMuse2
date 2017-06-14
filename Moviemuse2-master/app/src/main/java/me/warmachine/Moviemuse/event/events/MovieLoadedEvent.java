package me.warmachine.Moviemuse.event.events;

import android.support.annotation.NonNull;

import me.warmachine.Moviemuse.entity.Movie;

public class MovieLoadedEvent {

    public final Movie movie;

    public MovieLoadedEvent(@NonNull Movie movie) {
        this.movie = movie;
    }

}
