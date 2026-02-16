import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileNameValidator {

	public static void main(String[] args) throws Exception {

		// test code
		// args = new String[7];
		// args[0] = "/run/media/user1/a";
		// args[1] = "true";
		// args[2] = "80";
		// args[3] = "true";
		// args[4] = "/run/media/user1";
		// args[5] = "music";
		// args[6] = "";
		
		final Path dir = Paths.get(args[0]);
		
		final boolean check_Non_AlphaNumeric_And_Dash_And_Underscore_And_Dot = Boolean.parseBoolean(args[1]);
		
		final int extraBufferOnSize = Integer.valueOf(args[2]);
		
		final boolean check_space_char = Boolean.parseBoolean(args[3]);
		
		final Path parentDirectoryAbsolutePath = Paths.get(args[4]);
		
		final String exclude = args[5]; 
		
		final String ignoreCharactersArg = args[6];
		final Set<Character> ignoreCharacters = ignoreCharactersArg
		        .chars()
		        .mapToObj(c -> (char) c)
		        .collect(Collectors.toSet());
		
		if (!parentDirectoryAbsolutePath.isAbsolute()) {
			throw new IllegalArgumentException("Path must be absolute: " + parentDirectoryAbsolutePath);
		}

		if (!dir.isAbsolute()) {
			throw new IllegalArgumentException("Path must be absolute: " + dir);
		}

		if (!Files.isDirectory(parentDirectoryAbsolutePath)) {
			throw new Exception("parentDirectoryPath does not exist or it is not directory");
		}

		if (!Files.isDirectory(dir)) {
			throw new Exception("dir does not exist or it is not directory");
		}
		
		if (!dir.toRealPath().startsWith(parentDirectoryAbsolutePath.toRealPath())) {
			throw new Exception("dir is not sub-path of parentDirectoryAbsolutePath");
		}

		validate(dir, check_Non_AlphaNumeric_And_Dash_And_Underscore_And_Dot, extraBufferOnSize,
				check_space_char, parentDirectoryAbsolutePath, exclude, ignoreCharacters);
	}

	private static final String PREFIX_MS_WINDOWS = "MS_WINDOWS_RULE___";
	private static final String PREFIX_NIX_FAMILY = "NIX_FAMILY_RULE___";
	private static final String PREFIX_BOTH = "BOTH_NIX_AND_MS_WINDOWS_RULE___";
	private static final String PREFIX_SUGGESTION = "SUGGESTION___";

	private static final String MINIMAL_DRIVE_PATH = "C:\\";
	private static final int MAX_FILE_NAME_WITH_PATH_LENGTH_WITHOUT_NULL_CHARACTER = 259 - MINIMAL_DRIVE_PATH.length();

	private static final List<String> WINDOWS_RESERVED_NAMES = Arrays.asList(
			"CON","PRN","AUX","NUL",
			"COM1","COM2","COM3","COM4","COM5","COM6","COM7","COM8","COM9",
			"LPT1","LPT2","LPT3","LPT4","LPT5","LPT6","LPT7","LPT8","LPT9");

	private static final Set<String> WINDOWS_RESERVED_SET = new HashSet<>(WINDOWS_RESERVED_NAMES);

	private static void invalid(String reason, Path path) {
		System.out.println(path + "\r\ninvalid reason: " + reason + "\r\n");
	}

	public static void validate(final Path directory,
								final boolean checkNonAlphaNumeric,
								final int extraBufferOnSize,
								final boolean check_space_char,
								final Path parentDirectoryAbsolutePath,
								final String exclude,
								final Set<Character> ignoreCharacters) throws Exception {
		
		final String base = "a-zA-Z0-9_.-";

		final String extra = ignoreCharacters.stream()
												.map(c -> {
													if ("\\^-$[]".indexOf(c) >= 0) {
														return "\\" + c;
													}
													return String.valueOf(c);
												})
												.collect(Collectors.joining());

		final Pattern safePattern = Pattern.compile("^[" + extra +  base + "]+$");

		Files.walkFileTree(directory, new SimpleFileVisitor<>() {

			private final Map<Path, Set<String>> caseInsensitiveNamesByDirectory = new HashMap<>();
			private final Map<Path, Set<String>> normalizedNamesByDirectory = new HashMap<>();
			private final Map<Path, Set<String>> nfdNamesByDirectory = new HashMap<>();
			
	          @Override
	          public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
	              caseInsensitiveNamesByDirectory.remove(dir);
	              normalizedNamesByDirectory.remove(dir);
	              nfdNamesByDirectory.remove(dir);
	              return FileVisitResult.CONTINUE;
	          }
	          
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!isExcluded(file, exclude, parentDirectoryAbsolutePath)) {
					check(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (isExcluded(dir, exclude, parentDirectoryAbsolutePath)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				check(dir);
				return FileVisitResult.CONTINUE;
			}

			private void check(Path path) {

				// we need this check because if parent is root
				// then it returns null.
				if (path.getParent() == null) return;

				final String originalName = path.getFileName().toString();
				final String normalized = Normalizer.normalize(originalName, Normalizer.Form.NFC);
				final String normalizedNFD = Normalizer.normalize(originalName, Normalizer.Form.NFD);
				final String upper = normalized.toUpperCase(Locale.ROOT);

				String windowsTrimmed = upper;
				while (windowsTrimmed.endsWith(" ") || windowsTrimmed.endsWith(".")) {
					windowsTrimmed = windowsTrimmed.substring(0, windowsTrimmed.length() - 1);
				}

				if (windowsTrimmed.equals(".")) {
					invalid(PREFIX_BOTH + "ONLY_DOT_IS_RESERVED", path);
				} else if (windowsTrimmed.equals("..")) {
					invalid(PREFIX_BOTH + "ONLY_DOUBLE_DOT_IS_RESERVED", path);
				}

				String base = windowsTrimmed;
				int dotIndex = windowsTrimmed.indexOf('.');
				if (dotIndex > 0) {
					base = windowsTrimmed.substring(0, dotIndex);
				}

				if (WINDOWS_RESERVED_SET.contains(base)) {
					invalid(PREFIX_MS_WINDOWS + "RESERVED_DEVICE_NAME_" + base, path);
				}

				final int parentDirectoryPathLength = parentDirectoryAbsolutePath.toAbsolutePath().toString().length();
				final int fullPathLength = path.toAbsolutePath().toString().length();

				if (fullPathLength - parentDirectoryPathLength > MAX_FILE_NAME_WITH_PATH_LENGTH_WITHOUT_NULL_CHARACTER - extraBufferOnSize) {
					invalid(PREFIX_MS_WINDOWS + "MAX_FILE_WITH_PATH_LENGTH", path);
				}

				if (originalName.endsWith(".")) {
					invalid(PREFIX_MS_WINDOWS + "CAN_NOT_END_WITH_DOT", path);
				}

				if (originalName.endsWith(" ")) {
					invalid(PREFIX_MS_WINDOWS + "CAN_NOT_END_WITH_SPACE", path);
				}

				if (originalName.startsWith(" ")) {
					invalid(PREFIX_SUGGESTION + "CAN_NOT_START_WITH_SPACE", path);
				}

				String windowsReservedChars = "*?:\"<>|\\";
				for (char c : windowsReservedChars.toCharArray()) {
					if (originalName.indexOf(c) >= 0) {
						invalid(PREFIX_MS_WINDOWS + "RESERVED_CHARACTER_" + c, path);
					}
				}

				if (originalName.indexOf('\0') >= 0) {
					invalid(PREFIX_BOTH + "NULL_CHARACTER_NOT_ALLOWED", path);
				}

				if (originalName.contains("/")) {
					invalid(PREFIX_NIX_FAMILY + "RESERVED_CHARACTER_/", path);
				}

				if (check_space_char && originalName.matches(".*[\\s].*")) {
				    invalid(PREFIX_SUGGESTION + "INCLUDES_SPACE_OR_WHITESPACE", path);
				}

				if (originalName.startsWith("-")) {
					invalid(PREFIX_SUGGESTION + "LEADING_DASH_CLI_RISK", path);
				}

				if (containsDangerousUnicode(originalName)) {
				    invalid(PREFIX_SUGGESTION + "INCLUDES_DANGEROUS_UNICODE_CHARACTER", path);
				}

				if (originalName.contains("'")) {
					invalid(PREFIX_SUGGESTION + "'_CHARACTER_IS_NOT_RECOMMENDED", path);
				}

				if (checkNonAlphaNumeric) {

					if (!safePattern.matcher(originalName).matches()) {
						invalid(PREFIX_SUGGESTION + "IS_NOT_ALPHA_NUMERIC_AND_EXCEPT_UNDERSCORE_AND_DASH_AND_DOT", path);
					}
				}

				Path parentDir = path.getParent();

				Set<String> namesInDir = caseInsensitiveNamesByDirectory
				        .computeIfAbsent(parentDir, k -> new HashSet<>());

				Set<String> normalizedSet = normalizedNamesByDirectory
				        .computeIfAbsent(parentDir, k -> new HashSet<>());

				Set<String> nfdSet = nfdNamesByDirectory
				        .computeIfAbsent(parentDir, k -> new HashSet<>());

				String lower = normalized.toLowerCase(Locale.ROOT);

				if (!namesInDir.add(lower)) {
				    invalid(PREFIX_MS_WINDOWS + "CASE_INSENSITIVE_NAME_COLLISION", path);
				}

				if (!normalizedSet.add(normalized)) {
				    invalid(PREFIX_BOTH + "UNICODE_NORMALIZATION_COLLISION", path);
				}

				if (!nfdSet.add(normalizedNFD)) {
				    invalid(PREFIX_BOTH + "UNICODE_NFD_NORMALIZATION_COLLISION", path);
				}
			}
		});
	}

	private static boolean isExcluded(Path path, String exclude, Path parentDirectoryAbsolutePath) {
		if (exclude == null || exclude.isBlank()) {
			return false;
		}
		
		// exclude = "test"
		// must exclude all below as regex:
		// *test*

		String relative = parentDirectoryAbsolutePath.relativize(path).toString()
				.replace('\\', '/');

		String normalizedExclude = exclude.replace('\\', '/');

		return relative.contains(normalizedExclude);
	}

	private static boolean containsDangerousUnicode(String s) {
	    return s.codePoints().anyMatch(cp -> 
	            isIsoControl(cp)
	         || isFormatChar(cp)
	         || isBidiOverride(cp)
	         || isNonCharacter(cp)
	    );
	}

	private static boolean isIsoControl(int cp) {
	    return Character.isISOControl(cp);
	}

	private static boolean isFormatChar(int cp) {
	    return Character.getType(cp) == Character.FORMAT; // Cf
	}

	private static boolean isBidiOverride(int cp) {
	    return cp == 0x202A  // LRE
	        || cp == 0x202B  // RLE
	        || cp == 0x202D  // LRO
	        || cp == 0x202E  // RLO
	        || cp == 0x202C  // PDF
	        || cp == 0x2066  // LRI
	        || cp == 0x2067  // RLI
	        || cp == 0x2068  // FSI
	        || cp == 0x2069; // PDI
	}

	private static boolean isNonCharacter(int cp) {
	    // U+FDD0–U+FDEF
	    if (cp >= 0xFDD0 && cp <= 0xFDEF) return true;

	    // Any code point ending with FFFE or FFFF
	    if ((cp & 0xFFFE) == 0xFFFE) return true;

	    return false;
	}
}
