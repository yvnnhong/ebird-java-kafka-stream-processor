# Java Notes for BirdStream Project

These notes summarize key Java concepts used in the BirdStream project for reference.

## Package Naming
- Package names are distinct if they differ in any component, including the last one.
- Example: `com.yvonne.birdstream.model` and `com.yvonne.birdstream.producer` are different packages due to the last component (`model` vs. `producer`).

## Encapsulation
- **Private fields**: Restrict direct access to a class's internal state for data hiding (e.g., `latitude` in `LocationData`).
- **Public getters**: Provide controlled, read-only access to private fields, allowing other classes (including those in different packages) to retrieve their values.
- **Public constructors**: Enable object creation from any package, initializing private fields safely.
- **Key Point**: Public getter methods allow access to private variables from other packages, maintaining encapsulation while enabling data retrieval.

## Access Modifiers
Java access levels, from most restrictive to least:
- **`private`**: Accessible only within the same class.
- **Package-private** (no modifier): Accessible only within the same package.
- **`protected`**: Accessible within the same package and in subclasses (using the `extends` keyword), even in different packages.
- **`public`**: Accessible everywhere.