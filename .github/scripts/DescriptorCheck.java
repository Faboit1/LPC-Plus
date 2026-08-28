import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;

/**
 * Checks the resources the server reads at start-up, which compiling alone never touches.
 *
 * <p>A broken plugin.yml is invisible to javac: it is just a resource, so the build stays
 * green and the plugin only fails when a server tries to load it. That happened once
 * already — an unquoted colon in a permission description made SnakeYAML abort the
 * document, and the whole descriptor was rejected.</p>
 *
 * <p>Run from the repository root after a build:
 * {@code java -cp "target/classes:$(cat target/cp.txt)" .github/scripts/DescriptorCheck.java}</p>
 */
public class DescriptorCheck {

	private static int failures;

	public static void main(final String[] args) throws Exception {
		final File classes = new File(args.length > 0 ? args[0] : "target/classes");

		final PluginDescriptionFile description = descriptor(new File(classes, "plugin.yml"));
		if (description != null) {
			mainClassExists(classes, description);
			commandsAndPermissions(description);
		}
		config(new File(classes, "config.yml"));

		if (failures > 0) {
			System.out.println("\n" + failures + " problem(s) found - the server would not load this build.");
			System.exit(1);
		}
		System.out.println("\nplugin.yml and config.yml are both valid.");
	}

	/** Parses plugin.yml exactly as the server does. */
	private static PluginDescriptionFile descriptor(final File file) {
		try (InputStream in = new FileInputStream(file)) {
			final PluginDescriptionFile description = new PluginDescriptionFile(in);
			pass("plugin.yml parses: " + description.getName() + " v" + description.getVersion());
			return description;
		} catch (final Exception rejected) {
			fail("plugin.yml is rejected by " + rejected.getClass().getSimpleName() + ": "
				+ String.valueOf(rejected.getMessage()).replace('\n', ' '));
			return null;
		}
	}

	/** A descriptor that names a class the jar does not contain fails at load time. */
	private static void mainClassExists(final File classes, final PluginDescriptionFile description) {
		final String main = description.getMain();
		if (new File(classes, main.replace('.', '/') + ".class").isFile()) {
			pass("main class is present: " + main);
		} else {
			fail("main class named in plugin.yml is missing from the build: " + main);
		}
	}

	/**
	 * Maven filters plugin.yml, so a typo in a property leaves a literal {@code ${...}}
	 * behind — which loads fine and then misbehaves at runtime.
	 */
	private static void commandsAndPermissions(final PluginDescriptionFile description) {
		final String rendered = description.getName() + ' ' + description.getVersion() + ' '
			+ description.getMain() + ' ' + description.getCommands() + ' ' + description.getPermissions();
		if (rendered.contains("${")) {
			fail("an unsubstituted ${...} placeholder survived resource filtering");
		} else {
			pass("every ${...} placeholder was substituted");
		}
		pass("commands parse: " + description.getCommands().keySet());
		pass("permissions parse: " + description.getPermissions().size() + " declared");
	}

	/** config.yml has to parse, and its emoji keys have to survive YAML's boolean coercion. */
	private static void config(final File file) throws Exception {
		final YamlConfiguration config = new YamlConfiguration();
		try {
			config.load(new java.io.InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			pass("config.yml parses");
		} catch (final Exception broken) {
			fail("config.yml is invalid: " + String.valueOf(broken.getMessage()).replace('\n', ' '));
			return;
		}
		if (Files.readAllBytes(file.toPath()).length == 0) {
			fail("config.yml is empty");
		}
		final ConfigurationSection emoji = config.getConfigurationSection("emoji.list");
		if (emoji == null) {
			fail("config.yml has no emoji.list section");
			return;
		}
		// YAML 1.1 reads bare yes/no/on/off as booleans, which would silently rename a
		// shortcode to ":true:". Values must be non-empty strings for the same reason.
		int bad = 0;
		for (final Map.Entry<String, Object> entry : emoji.getValues(false).entrySet()) {
			final String key = entry.getKey();
			if ("true".equals(key) || "false".equals(key)) {
				fail("emoji shortcode '" + key + "' was coerced from a YAML boolean - quote the key");
				bad++;
			}
			if (!(entry.getValue() instanceof String) || ((String) entry.getValue()).isEmpty()) {
				fail("emoji shortcode '" + key + "' has a non-string or empty value");
				bad++;
			}
		}
		if (bad == 0) {
			pass("emoji.list is sound: " + emoji.getValues(false).size() + " shortcodes");
		}
	}

	private static void pass(final String what) {
		System.out.println("  OK    " + what);
	}

	private static void fail(final String what) {
		System.out.println("  FAIL  " + what);
		failures++;
	}
}
