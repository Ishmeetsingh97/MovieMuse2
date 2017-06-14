package me.warmachine.Moviemuse.event.events;

import me.warmachine.Moviemuse.entity.Movie;

public class UpdateMEvent {

    public final Movie movie;

    public UpdateMEvent(Movie movie) {
        this.movie = movie;
    }

}
