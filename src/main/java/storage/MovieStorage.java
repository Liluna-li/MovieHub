package storage;

import model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MovieStorage {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public List<Movie> findAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie findById(int id) {
        return movies.get(id);
    }

    public List<Movie> findByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie m : movies.values()) {
            if (m.getYear() == year) {
                result.add(m);
            }
        }
        return result;
    }

    public Movie add(String title, int year) {
        int id = nextId.getAndIncrement();
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    public boolean remove(int id) {
        return movies.remove(id) != null;
    }
}