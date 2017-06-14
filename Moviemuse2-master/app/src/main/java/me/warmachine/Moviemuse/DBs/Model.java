package me.warmachine.Moviemuse.DBs;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.realm.RealmList;
import me.warmachine.Moviemuse.entity.Movie;
import me.warmachine.Moviemuse.entity.Result;
import me.warmachine.Moviemuse.entity.Review;
import me.warmachine.Moviemuse.entity.Video;
import me.warmachine.Moviemuse.event.DataBusProvider;
import me.warmachine.Moviemuse.event.events.ApiError;
import me.warmachine.Moviemuse.event.events.CancelAll;
import me.warmachine.Moviemuse.event.events.LoadMovie;
import me.warmachine.Moviemuse.event.events.LoadMovies;
import me.warmachine.Moviemuse.event.events.MovieLoadedEvent;
import me.warmachine.Moviemuse.event.events.MovieUpdatedEvent;
import me.warmachine.Moviemuse.event.events.MoviesLoadedEvent;
import me.warmachine.Moviemuse.event.events.UpdateMEvent;
import me.warmachine.Moviemuse.Effects.AppUtil;
import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;

public class Model {

    private static final String TAG = "Model";
    private static final String BASE_URL = "http://api.themoviedb.org/3/";

    private final DB mDB;
    private final TMDBApi mApiService;
    private final String mApiKey;
    private final Map<String, Call> mPendingCalls = new ConcurrentHashMap<>();

    public Model() {
        mDB = new DB();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Date.class, new DateDeserializer())
                .registerTypeAdapter(new TypeToken<RealmList<Video>>() {}.getType(), new VideoRealmListDeserializer())
                .registerTypeAdapter(new TypeToken<RealmList<Review>>() {}.getType(), new ReviewRealmListDeserializer())
                .setExclusionStrategies(new Realm())
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        mApiService = retrofit.create(TMDBApi.class);
        mApiKey = "1857d1a327dad4f2be51a0cb9cd00979";
        getDataBus().register(this);
    }

    @Subscribe
    public void onLoadMoviesEvent(final LoadMovies event) {
        if (event.sortCriteria == Result.SortCriteria.FAVORITES) {
            mDB.loadFavoriteMovies(new DB.ReadCallback<List<Movie>>() {
                @Override
                public void done(List<Movie> favorites) {
                    getDataBus().post(new MoviesLoadedEvent(favorites, event.sortCriteria));
                }
            });
            return;
        }
        Call<Result> call = mApiService.fetchMovies(mApiKey, event.sortCriteria.str);
        enqueue(event.getClass().getSimpleName(), call, new ApiCallback<Result>() {
            @Override
            public void onApiResponse(Result result, Retrofit retrofit) {
                updateMoviesInDb(result.getResults(), event.sortCriteria);
            }

            @Override
            public void onApiFailure(Throwable throwable) {
                getDataBus().post(new ApiError(event, throwable));
            }
        });
    }

    private void updateMoviesInDb(final List<Movie> movies, final Result.SortCriteria sortCriteria) {
        mDB.loadFavoriteMovies(new DB.ReadCallback<List<Movie>>() {
            @Override
            public void done(List<Movie> favorites) {
                for (Movie m : movies)
                    for (Movie f : favorites)
                        if (m.getId() == f.getId())
                            m.setFavorite(true);
                mDB.createOrUpdateEntity(movies, new DB.WriteCallback() {
                    @Override
                    public void done() {
                        getDataBus().post(new MoviesLoadedEvent(movies, sortCriteria));
                    }
                });
            }
        });
    }

    @Subscribe
    public void onLoadMovieEvent(final LoadMovie event) {
        mDB.loadMovie(event.id, new DB.ReadCallback<Movie>() {
            @Override
            public void done(final Movie localMovie) {
                getDataBus().post(new MovieLoadedEvent(localMovie));
                if (localMovie.getReviews().isEmpty() && localMovie.getVideos().isEmpty()) {

                    fetchMovie(localMovie, event);
                }
            }
        });
    }

    private void fetchMovie(final Movie localMovie, final LoadMovie event) {
        Call<Movie> call = mApiService.fetchMovie(event.id, mApiKey);
        enqueue(event.getClass().getSimpleName(), call, new ApiCallback<Movie>() {
            @Override
            public void onApiResponse(Movie fetchedMovie, Retrofit retrofit) {
                fetchedMovie.setFavorite(localMovie.isFavorite());
                mDB.createOrUpdateEntity(fetchedMovie, new DB.WriteCallback() {
                    @Override
                    public void done() {
                        // send over updated movie details
                        readMovieFromDb(event.id);
                    }
                });
            }

            @Override
            public void onApiFailure(Throwable throwable) {
                getDataBus().post(new ApiError(event, throwable));
            }
        });
    }

    private void readMovieFromDb(int movieId) {
        mDB.loadMovie(movieId, new DB.ReadCallback<Movie>() {
            @Override
            public void done(Movie movie) {
                getDataBus().post(new MovieLoadedEvent(movie));
            }
        });
    }

    @Subscribe
    public void onUpdateMovieEvent(final UpdateMEvent event) {
        final Movie movieCopy = AppUtil.copy(event.movie, Movie.class);
        if (movieCopy != null) {
            mDB.createOrUpdateEntity(movieCopy, new DB.WriteCallback() {
                @Override
                public void done() {
                    // send over a copy of the updated movie
                    getDataBus().post(new MovieLoadedEvent(movieCopy));
                    getDataBus().post(new MovieUpdatedEvent(movieCopy));
                }
            });
        }
    }

    @Subscribe
    public void onCancelAllEvent(CancelAll event) {
        for (Call call : mPendingCalls.values()) {
            call.cancel();
        }
        mPendingCalls.clear();
    }

    private <T> void enqueue(String tag, Call<T> call, ApiCallback<T> apiCallback) {

        Call pendingRequest = mPendingCalls.remove(tag);
        if (pendingRequest != null) {
            pendingRequest.cancel();
        }

        mPendingCalls.put(tag, call);
        apiCallback.setCall(call);
        call.enqueue(apiCallback);
    }

    private void removePendingCall(Call call) {
        if (call == null) {
            return;
        }
        for (Map.Entry<String, Call> entry : mPendingCalls.entrySet()) {
            if (call == entry.getValue()) {
                mPendingCalls.remove(entry.getKey());
            }
        }
    }

    private abstract class ApiCallback<T> implements Callback<T> {
        private Call<T> mCall;

        public void setCall(Call<T> call) {
            mCall = call;
        }

        @Override
        public void onResponse(Response<T> response, Retrofit retrofit) {
            removePendingCall(mCall);
            if (response.body() != null) {
                onApiResponse(response.body(), retrofit);
            } else if (response.errorBody() != null) {
                try { Log.e(TAG, response.errorBody().string()); } catch (IOException ignored) {}
            } else {
                Log.e(TAG, "response.body() and response.errorBody() are both null!");
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            removePendingCall(mCall);
            Log.e(TAG, "API error: " + throwable.getMessage());
            Log.e(TAG, Log.getStackTraceString(throwable));
            onApiFailure(throwable);
        }

        public abstract void onApiResponse(T response, Retrofit retrofit);

        public void onApiFailure(Throwable throwable) {}
    }

    private Bus getDataBus() {
        return DataBusProvider.getBus();
    }

}
