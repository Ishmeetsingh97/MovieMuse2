package me.warmachine.Moviemuse.Basic;

import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.squareup.otto.Subscribe;

import butterknife.ButterKnife;
import me.warmachine.Moviemuse.R;
import me.warmachine.Moviemuse.entity.Movie;
import me.warmachine.Moviemuse.event.events.MovieLoadedEvent;

public class MovieDetailsActivity extends BaseActivity {

    private static final String TAG = "DetailsActivity";

    private Movie mMovie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        ButterKnife.bind(this);

        mMovie = Movie.fromParcelable(getIntent().getExtras().getParcelable(BundleKeys.MOVIE));

        if (savedInstanceState == null) {
            Fragment detailsFragment = MovieDetailsFragment.newInstance(mMovie, false);
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, detailsFragment,
                            MovieDetailsFragment.class.getSimpleName())
                    .commit();
        }
    }

    @Subscribe
    public void onMovieLoadedEvent(MovieLoadedEvent event) {
        mMovie = event.movie;
    }

}
