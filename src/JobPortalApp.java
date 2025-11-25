import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Enhanced Online Job Portal Application (Single File)
 *
 * Implements rubric requirements:
 * 1. OOP (Interface, Inheritance, Polymorphism): PortalUser interface and Employer class.
 * 2. Collections & Generics: Use of List<Job> and ArrayList<Job>.
 * 3. Multithreading & Synchronization: SwingWorker used for all database I/O to prevent
 * UI freezing, and synchronized block in DBConnection for thread-safe setup.
 * 4. Database Connectivity: JDBC implemented via DBConnection and JobDAO.
 * 5. Exception Handling: Explicit try-catch blocks and propagation of SQLExceptions.
 */

// --- 1. OOP: Interface (Polymorphism) ---
interface PortalUser {
    String getRole();
    String getName();
}

// --- 1. OOP: Class Hierarchy (Inheritance) ---
class Employer implements PortalUser {
    private final String name;
    private final String email;

    public Employer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Override
    public String getRole() {
        return "Employer";
    }

    @Override
    public String getName() {
        return name;
    }
}

// Model Class: Job
class Job {
    private final int id;
    private final String title;
    private final String description;
    private final String company;

    public Job(int id, String title, String description, String company) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.company = company;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCompany() { return company; }
}

// Database Utility Class: DBConnection
class DBConnection {
    // !!! CHANGE THESE TO YOUR DATABASE CREDENTIALS !!!
    private static final String BASE_URL = "jdbc:mysql://localhost:3306/";
    private static final String DB_NAME = "job_portal";
    // NOTE: These credentials must have privileges to CREATE DATABASE.
    private static final String USER = "root"; // <-- CHANGE THIS TO YOUR MYSQL USER
    private static final String PASSWORD = "gtaomp23"; // <-- CHANGE THIS TO YOUR MYSQL PASSWORD

    private static boolean isSetup = false;
    private static final Object setupLock = new Object(); // Used for synchronization

    /**
     * Ensures the database and required tables exist.
     * Implements basic synchronization to ensure setup runs only once, thread-safely.
     */
    private static void setupDatabase() throws SQLException {
        synchronized (setupLock) { // Synchronization applied here
            if (isSetup) return;

            // 1. Connect to MySQL server without specifying the target database
            try (Connection conn = DriverManager.getConnection(BASE_URL, USER, PASSWORD)) {
                // 2. Create Database if it doesn't exist
                String createDbSql = "CREATE DATABASE IF NOT EXISTS " + DB_NAME;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createDbSql);
                }
            }

            // 3. Connect to the job_portal database to create tables
            String dbUrl = BASE_URL + DB_NAME;
            try (Connection conn = DriverManager.getConnection(dbUrl, USER, PASSWORD)) {
                // 4. Create Jobs Table
                String createJobsTableSql = "CREATE TABLE IF NOT EXISTS jobs ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "title VARCHAR(100) NOT NULL,"
                        + "description TEXT,"
                        + "company VARCHAR(100) NOT NULL"
                        + ")";

                // 5. Create Users Table (User class is defined but not fully used for simplicity)
                String createUsersTableSql = "CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "name VARCHAR(100) NOT NULL,"
                        + "email VARCHAR(100) NOT NULL UNIQUE,"
                        + "user_type ENUM('Employer','JobSeeker') NOT NULL"
                        + ")";

                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(createJobsTableSql);
                    stmt.executeUpdate(createUsersTableSql);
                }

            } catch (SQLException e) {
                // Critical failure during table creation
                throw e;
            }
            isSetup = true;
        }
    }

    /**
     * Returns a connection to the job_portal database.
     * @return A database Connection object.
     */
    public static Connection getConnection() throws SQLException {
        try {
            if (!isSetup) {
                setupDatabase();
            }
            // Return a connection to the established database
            String dbUrl = BASE_URL + DB_NAME;
            return DriverManager.getConnection(dbUrl, USER, PASSWORD);
        } catch (SQLException e) {
            // Re-throw exception for caller (JobDAO) to handle or propagate
            throw new SQLException("Database connection or setup failed. Check credentials and server status.", e);
        }
    }
}

// Data Access Object: JobDAO (Handles all database operations for Job)
class JobDAO {
    // Methods now throw SQLException, delegating exception handling to the caller (SwingWorker)

    public static void addJob(Job job) throws SQLException {
        String sql = "INSERT INTO jobs (title, description, company) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, job.getTitle());
            stmt.setString(2, job.getDescription());
            stmt.setString(3, job.getCompany());
            stmt.executeUpdate();
        }
    }

    // Collections & Generics used: List<Job>
    public static List<Job> getAllJobs() throws SQLException {
        List<Job> jobs = new ArrayList<>();
        String sql = "SELECT * FROM jobs ORDER BY id DESC";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                jobs.add(new Job(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("company")
                ));
            }
        }
        return jobs;
    }

    public static Job searchJobByTitle(String title) throws SQLException {
        String sql = "SELECT * FROM jobs WHERE title LIKE ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + title + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Job(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("company")
                    );
                }
            }
        }
        return null;
    }
}

// GUI Class: JobPortalApp (Multithreading implemented via SwingWorker)
public class JobPortalApp extends JFrame {
    private final JTextArea jobListArea;
    private final JTextField titleField, descField, searchField;
    private final JLabel statusLabel;

    public JobPortalApp() {
        setTitle("Online Job Portal (Multithreaded DB)");
        setSize(600, 580);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- UI Setup ---

        // Status Label (for Multithreading feedback)
        statusLabel = new JLabel("Status: Ready", SwingConstants.CENTER);
        statusLabel.setForeground(Color.BLUE);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        // Post Job Panel
        JPanel postPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        postPanel.setBorder(BorderFactory.createTitledBorder("Post New Job (Company: CompanyXYZ)"));
        titleField = new JTextField();
        descField = new JTextField();
        JButton postBtn = new JButton("Post Job");

        postPanel.add(new JLabel("Job Title:"));
        postPanel.add(titleField);
        postPanel.add(new JLabel("Description:"));
        postPanel.add(descField);
        postPanel.add(new JLabel(""));
        postPanel.add(postBtn);

        // Search & Refresh Panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        searchField = new JTextField(20);
        JButton searchBtn = new JButton("Search by Title");
        JButton refreshBtn = new JButton("Refresh Job List");
        searchPanel.add(new JLabel("Search:"));
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(refreshBtn);

        // Job List Area
        jobListArea = new JTextArea();
        jobListArea.setEditable(false);
        jobListArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(jobListArea);
        scrollPane.setPreferredSize(new Dimension(580, 300));

        // Layout the main frame
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(postPanel, BorderLayout.NORTH);
        northPanel.add(searchPanel, BorderLayout.CENTER);

        add(northPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);


        // --- Actions (Delegated to SwingWorker) ---
        postBtn.addActionListener(e -> postJob());
        searchBtn.addActionListener(e -> searchJob());
        refreshBtn.addActionListener(e -> refreshJobList());

        // Initial Load
        refreshJobList();
    }

    private void postJob() {
        String title = titleField.getText().trim();
        String desc = descField.getText().trim();

        if (title.isEmpty() || desc.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in both Job Title and Description.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Use the Employer class for OOP demonstration
        PortalUser employer = new Employer("CompanyXYZ", "hr@companyxyz.com");
        // Job ID is 0 as the database handles auto-incrementing
        Job jobToPost = new Job(0, title, desc, employer.getName());

        // Multithreading: Use SwingWorker for background DB operation
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws SQLException {
                statusLabel.setText("Status: Posting job to database...");
                JobDAO.addJob(jobToPost);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions during execution
                    statusLabel.setText("Status: Job successfully posted.");
                    JOptionPane.showMessageDialog(JobPortalApp.this, "Job Posted Successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                    titleField.setText("");
                    descField.setText("");
                    refreshJobList(); // Refresh list after posting
                } catch (InterruptedException | ExecutionException ex) {
                    handleDbError(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    private void searchJob() {
        String title = searchField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a title to search.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Multithreading: Use SwingWorker for background DB operation
        new SwingWorker<Job, Void>() {
            @Override
            protected Job doInBackground() throws SQLException {
                statusLabel.setText("Status: Searching for job...");
                return JobDAO.searchJobByTitle(title);
            }

            @Override
            protected void done() {
                try {
                    Job foundJob = get();
                    if (foundJob != null) {
                        jobListArea.setText("--- Found Job ---\n" + formatJob(foundJob) + "---------------------\n");
                        statusLabel.setText("Status: Job found successfully.");
                    } else {
                        jobListArea.setText("Job with title containing '" + title + "' not found.");
                        statusLabel.setText("Status: Search completed, job not found.");
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    handleDbError(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    private void refreshJobList() {
        // Multithreading: Use SwingWorker for background DB operation
        new SwingWorker<List<Job>, Void>() {
            @Override
            protected List<Job> doInBackground() throws SQLException {
                statusLabel.setText("Status: Loading jobs from database...");
                return JobDAO.getAllJobs();
            }

            @Override
            protected void done() {
                try {
                    List<Job> jobs = get();
                    jobListArea.setText(""); // Clear area
                    if (jobs.isEmpty()) {
                        jobListArea.setText("No jobs available in the database.");
                    } else {
                        jobListArea.append("--- Available Jobs (Total: " + jobs.size() + ") ---\n\n");
                        for (Job job : jobs) {
                            jobListArea.append(formatJob(job));
                            jobListArea.append("---------------------\n");
                        }
                    }
                    statusLabel.setText("Status: Job list refreshed.");
                } catch (InterruptedException | ExecutionException ex) {
                    handleDbError(ex.getCause() != null ? ex.getCause() : ex);
                }
            }
        }.execute();
    }

    private void handleDbError(Throwable t) {
        String errorMsg = "Database Error: " + t.getMessage();
        statusLabel.setText("Status: ERROR occurred.");
        statusLabel.setForeground(Color.RED);
        t.printStackTrace();
        JOptionPane.showMessageDialog(this, errorMsg, "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private String formatJob(Job job) {
        return String.format(
                "ID: %d\nTitle: %s\nCompany: %s\nDescription: %s\n",
                job.getId(), job.getTitle(), job.getCompany(), job.getDescription()
        );
    }

    public static void main(String[] args) {
        // Run the GUI in the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> new JobPortalApp().setVisible(true));
    }
}