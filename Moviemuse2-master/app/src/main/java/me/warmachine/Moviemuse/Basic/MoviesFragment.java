package me.warmachine.Moviemuse.Basic;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.BindDimen;
import butterknife.ButterKnife;
import me.warmachine.Moviemuse.BuildConfig;
import me.warmachine.Moviemuse.R;
import me.warmachine.Moviemuse.entity.Movie;
import me.warmachine.Moviemuse.entity.Result;
import me.warmachine.Moviemuse.event.events.ApiError;
import me.warmachine.Moviemuse.event.events.LoadMovies;
import me.warmachine.Moviemuse.event.events.MovieUpdatedEvent;
import me.warmachine.Moviemuse.event.events.MoviesLoadedEvent;
import me.warmachine.Moviemuse.Effects.AppUtil;

public class MoviesFragment extends BaseFragment implements
        SwipeRefreshLayout.OnRefreshListener, Toolbar.OnMenuItemClickListener {

    private static final String TAG = "MoviesFragment";

    private static final String KEY_MOVIES = "movies";
    private static final String KEY_SORT_ORDER = Result.SortCriteria.class.getSimpleName();

    public static final Result.SortCriteria DEFAULT_SORT_CRITERIA =
            Result.SortCriteria.POPULARITY;

    @Bind(R.id.toolbar)                 Toolbar mToolbar;
    @Bind(R.id.movies_list)             RecyclerView objMoviesListView;
    @Bind(R.id.swipe_refresh_layout)    SwipeRefreshLayout mSwipeRefreshLayout;

    @BindColor(android.R.color.white)           int mCurrentTitleTextColor;
    @BindDimen(R.dimen.desired_column_width)    int desiredColumnWidth;

    private MoviesAdapter objMoviesAdapter;
    private ArrayList<Movie> objMovies = new ArrayList<>();
    private Result.SortCriteria mCurrentSortCriteria = DEFAULT_SORT_CRITERIA;
    private boolean mDetailsUpdatePending = false;

    public MoviesFragment() {}

    @SuppressWarnings("unused")
    public static MoviesFragment newInstance() {
        return new MoviesFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View view = inflater.inflate(R.layout.fragment_movies, container, false);
        ButterKnife.bind(this, view);

        final Activity activity = getActivity();
        String title = getString(R.string.app_name);
        AppUtil.setupToolbar(activity, mToolbar, AppUtil.ToolbarNavIcon.NONE, title);
        mToolbar.inflateMenu(R.menu.menu_movies);
        mToolbar.setOnMenuItemClickListener(this);

        objMoviesListView.setHasFixedSize(true);

        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // compute optimal number of columns based on available width
                int gridWidth = objMoviesListView.getWidth();
                int optimalColumnCount = Math.max(Math.round((1f*gridWidth) / desiredColumnWidth), 1);
                int actualPosterViewWidth = gridWidth / optimalColumnCount;

                RecyclerView.LayoutManager layoutManager = new GridLayoutManager(activity, optimalColumnCount);
                objMoviesListView.setLayoutManager(layoutManager);

                objMoviesAdapter = new MoviesAdapter(activity, objMovies, actualPosterViewWidth,
                        (MoviesAdapter.ClickListener) activity);
                objMoviesListView.setAdapter(objMoviesAdapter);

                if (savedInstanceState != null) {
                    String enumName = savedInstanceState.getString(KEY_SORT_ORDER);
                    mCurrentSortCriteria = Result.SortCriteria.valueOf(enumName);
                    List<Parcelable> parcelables = savedInstanceState.getParcelableArrayList(KEY_MOVIES);
                    if (parcelables != null) {
                        showMovies(Movie.fromParcelable(parcelables));
                    }
                }
            }
        });

        mSwipeRefreshLayout.setOnRefreshListener(this);
        mSwipeRefreshLayout.setColorSchemeResources(R.color.colorAccent, R.color.colorPrimary);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (objMovies.isEmpty() || mCurrentSortCriteria == Result.SortCriteria.FAVORITES) {
            onRefresh();
        }
    }

    @Override
    public void onStop() {
        stopRefreshing();
        super.onStop();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_sort_popularity:
                setSortCriteria(Result.SortCriteria.POPULARITY);
                return true;
            case R.id.action_sort_rating:
                setSortCriteria(Result.SortCriteria.RATING);
                return true;
            case R.id.action_filter_favorites:
                setSortCriteria(Result.SortCriteria.FAVORITES);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SORT_ORDER, mCurrentSortCriteria.name());
        outState.putParcelableArrayList(KEY_MOVIES, Movie.toParcelable(objMovies));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        String enumName = savedInstanceState.getString(Result.SortCriteria.class.getSimpleName());
        mCurrentSortCriteria = Result.SortCriteria.valueOf(enumName);
    }

    public void setSortCriteria(Result.SortCriteria criteria) {
        if (mCurrentSortCriteria != criteria) {
            mSwipeRefreshLayout.setRefreshing(true);
            getDataBus().post(new LoadMovies(criteria));
        }
    }

    @Override
    public void onRefresh() {
        getDataBus().post(new LoadMovies(mCurrentSortCriteria));
    }

    public void stopRefreshing() {
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Subscribe
    public void onMoviesLoadedEvent(MoviesLoadedEvent event) {
        mCurrentSortCriteria = event.sortCriteria;
        showMovies(event.movies);
        if (mDetailsUpdatePending && mCurrentSortCriteria == Result.SortCriteria.FAVORITES) {
            Movie firstMovie = objMovies.isEmpty() ? null : objMovies.get(0);
            ((MoviesActivity) getActivity()).showMovieDetails(firstMovie);
            mDetailsUpdatePending = false;
        }
    }

    @Subscribe
    public void onMovieUpdatedEvent(MovieUpdatedEvent event) {
        // if a movie was updated and we're currently in the favorites list, that means
        // the two-pane layout is visible and the favorites list itself needs to be updated
        // also the current movie was un-favorited, so tell the activity to update the details pane
        if (mCurrentSortCriteria == Result.SortCriteria.FAVORITES) {
            getDataBus().post(new LoadMovies(mCurrentSortCriteria));
            mDetailsUpdatePending = true;
        }
    }

    private void showMovies(@NonNull List<Movie> movies) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Loaded " + movies.size() + " movies:");
            for (Movie movie : movies) {
                Log.v(TAG, movie.getTitle() + " (poster: " + movie.getPosterPath() + ")");
            }
        }
        objMovies.clear();
        objMovies.addAll(movies);
        objMoviesAdapter.notifyDataSetChanged();
        stopRefreshing();
    }

    public void setPalette(int primaryColor, int primaryDarkColor, int titleTextColor,
                           boolean animate) {
        AppUtil.setColorTheme(getActivity(), mToolbar, primaryColor, primaryDarkColor,
                titleTextColor, mCurrentTitleTextColor, animate);
        mCurrentTitleTextColor = titleTextColor;
    }

    @Subscribe
    public void onApiErrorEvent(ApiError event) {
        if (event.sourceEvent instanceof LoadMovies && getView() != null) {
            Snackbar.make(getView(), R.string.api_error, Snackbar.LENGTH_LONG).show();
        }
    }

}
