package me.warmachine.Moviemuse.event.events;

import android.support.annotation.NonNull;

import me.warmachine.Moviemuse.entity.Movie;

public final class MovieUpdatedEvent {

    public final Movie movie;

    public MovieUpdatedEvent(@NonNull Movie movie) {
        this.movie = movie;
    }

}
