package me.warmachine.Moviemuse.event.events;

// represents a network / API related error
public final class ApiError implements Api {

    public final Api sourceEvent;
    public final Throwable throwable;

    public ApiError(Api sourceEvent, Throwable throwable) {
        this.sourceEvent = sourceEvent;
        this.throwable = throwable;
    }

}
