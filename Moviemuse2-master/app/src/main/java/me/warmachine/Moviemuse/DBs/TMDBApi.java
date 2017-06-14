package me.warmachine.Moviemuse.DBs;

import me.warmachine.Moviemuse.entity.Movie;
import me.warmachine.Moviemuse.entity.Result;
import retrofit.Call;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

interface TMDBApi {

    @GET("discover/movie?vote_count.gte=250")
    Call<Result> fetchMovies(@Query("api_key") String apiKey, @Query("sort_by") String sortBy);

    @GET("movie/{id}?append_to_response=videos,reviews")
    Call<Movie> fetchMovie(@Path("id") int id, @Query("api_key") String apiKey);

}
