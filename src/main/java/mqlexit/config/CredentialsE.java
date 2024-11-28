package config;





public class CredentialsE {
    private final String username;
    private final String password;

    public CredentialsE(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}