# ABOUT THE PROJECT

Detects the invalid file names for MS Windows and Unix-likes.

That means, you can run this code from any OS. If the files are valid, all those files can be moved on any OS without any issue.

All rules:

- https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file ("web.archive.org" and "archive.is". archived date: 2020)

- Checks if / exist (to support Unix-likes filesystems)

- Checks the lenght

- Additional (non-required) suggestions

# Run

only java runtime 25 is required.

All parameters are required.

Below command should be run in this directory.

```sh
java FileNameValidator.java \
  /myFiles/archive/games \
  true \
  30 \
  true \
  /myFiles/archive \
  mario \
  '+]'
```

Args:

- directory to validate
- check_Non_AlphaNumeric_And_Dash_And_Underscore_And_Dot
- extraBufferOnSize
- check_space_char
- root path (if you gonna copy from current OS to another).
- exclude (simple algorithm: if any path or file name or both contains this, it will be excluded). if empty, must be sent empty string.
- ignore character list (for suggestions only). if empty, must be sent empty string.

