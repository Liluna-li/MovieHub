import server.MoviesServer;

public class Main {
    public static void main(String[] args) throws Exception {
        MoviesServer server = new MoviesServer(8080);
        server.start();
        System.out.println("MovieHub запущен на http://localhost:8080");
    }
}