// Function to validate a single environment variable
function isValidEnvironmentVariable(variableName: string): boolean {
    // Regular expression for validating strings that are not empty and contain only letters (a-z)
    const regex = /^[a-zA-Z]+$/;
    
    if (!regex.test(variableName)) {
        console.error(`Invalid environment variable name "${variableName}". Expected it to be a non-empty string containing only letter characters.`);
        return false;
    }
    
    return true;
}

// Array of environment variables with potential invalid values
const potentiallyInvalidVariables = ['&', '<>', '>';

for (let i = 0; i < potentiallyInvalidVariables.length; i++) {
    let validVariableName = '';
  
    try {
        // Attempt to read the current value from the original format
        validVariableName += process.env[possiblyInvalidVariables[i]];
        
        isValidEnvironmentVariable(validVariableName);
    } catch (error) {
        console.error(`Failed to validate "${possibleInvalidVariables[i]}"`, error.message);
    }

    if (!isValidEnvironmentVariable(valid VariableName)) {
        const correctedValue = `env.${validVariableName}`;
        
        // Update the environment variable with a sanitized version
        process.env[correctedValue] = correctValue;
        
        console.log(`Sanitized ${possibleInvalidVariables [i]} to ${correctedValue}`);
    }
}