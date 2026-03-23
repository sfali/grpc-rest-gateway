#!/bin/bash

# Script to verify YAML file generation for gRPC REST Gateway
# This script checks that OpenAPI YAML files are generated correctly during compilation

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo "=== Verifying YAML Generation for gRPC REST Gateway ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if YAML files exist in a directory
check_yaml_files() {
    local module=$1
    local scala_version=$2
    local specs_dir=$3
    
    local target_dir="$PROJECT_ROOT/$module/target/jvm-$scala_version/resource_managed/main/$specs_dir"
    local classes_dir="$PROJECT_ROOT/$module/target/jvm-$scala_version/classes/$specs_dir"
    
    echo "Checking $module (Scala $scala_version)..."
    
    # Check resource_managed directory
    if [ -d "$target_dir" ]; then
        local yaml_count=$(find "$target_dir" -name "*.yml" -type f | wc -l | tr -d ' ')
        if [ "$yaml_count" -gt 0 ]; then
            echo -e "${GREEN}✓${NC} Found $yaml_count YAML file(s) in resource_managed"
            find "$target_dir" -name "*.yml" -type f | sed 's|.*/||' | sed 's/^/  - /'
        else
            echo -e "${RED}✗${NC} No YAML files found in resource_managed"
            return 1
        fi
    else
        echo -e "${YELLOW}⚠${NC} Directory not found: $target_dir"
        echo "  Run 'sbt ${module}JVM${scala_version/./}/compile' to generate files"
        return 1
    fi
    
    # Check classes directory (after resourceGenerators)
    if [ -d "$classes_dir" ]; then
        local classes_yaml_count=$(find "$classes_dir" -name "*.yml" -type f | wc -l | tr -d ' ')
        if [ "$classes_yaml_count" -gt 0 ]; then
            echo -e "${GREEN}✓${NC} Found $classes_yaml_count YAML file(s) in classes (included in classpath)"
        else
            echo -e "${YELLOW}⚠${NC} No YAML files in classes directory"
        fi
    fi
    
    echo ""
    return 0
}

# Compile projects if needed
echo "Step 1: Compiling projects..."
echo "Running: sbt 'e2e-nettyJVM2_13/compile; e2e-pekkoJVM2_13/compile'"
sbt -batch 'e2e-nettyJVM2_13/compile; e2e-pekkoJVM2_13/compile' > /dev/null 2>&1 || {
    echo -e "${RED}✗${NC} Compilation failed. Please check for errors."
    exit 1
}
echo -e "${GREEN}✓${NC} Compilation successful"
echo ""

# Check YAML files
echo "Step 2: Verifying YAML files..."
echo ""

success=true

check_yaml_files "e2e-netty" "2.13" "specs" || success=false
check_yaml_files "e2e-pekko" "2.13" "specs" || success=false

# Optional: Check other Scala versions if they exist
if [ -d "$PROJECT_ROOT/e2e-netty/target/jvm-2.12" ]; then
    check_yaml_files "e2e-netty" "2.12" "specs-2.12" || success=false
fi

if [ -d "$PROJECT_ROOT/e2e-pekko/target/jvm-2.12" ]; then
    check_yaml_files "e2e-pekko" "2.12" "specs-2.12" || success=false
fi

if [ -d "$PROJECT_ROOT/e2e-netty/target/jvm-3" ]; then
    check_yaml_files "e2e-netty" "3" "" || success=false
fi

if [ -d "$PROJECT_ROOT/e2e-pekko/target/jvm-3" ]; then
    check_yaml_files "e2e-pekko" "3" "" || success=false
fi

echo "Step 3: Validating YAML content..."
echo ""

# Check a sample YAML file for basic structure
sample_yaml="$PROJECT_ROOT/e2e-netty/target/jvm-2.13/classes/specs/TestServiceB.yml"
if [ -f "$sample_yaml" ]; then
    echo "Validating sample file: TestServiceB.yml"
    
    # Check for required OpenAPI fields
    if grep -q "openapi: 3.1.0" "$sample_yaml"; then
        echo -e "${GREEN}✓${NC} OpenAPI version 3.1.0 found"
    else
        echo -e "${RED}✗${NC} OpenAPI version 3.1.0 not found"
        success=false
    fi
    
    if grep -q "info:" "$sample_yaml" && grep -q "version:" "$sample_yaml"; then
        echo -e "${GREEN}✓${NC} Info section with version found"
    else
        echo -e "${RED}✗${NC} Info section incomplete"
        success=false
    fi
    
    if grep -q "paths:" "$sample_yaml"; then
        echo -e "${GREEN}✓${NC} Paths section found"
    else
        echo -e "${RED}✗${NC} Paths section not found"
        success=false
    fi
    
    if grep -q "components:" "$sample_yaml" && grep -q "schemas:" "$sample_yaml"; then
        echo -e "${GREEN}✓${NC} Components/schemas section found"
    else
        echo -e "${RED}✗${NC} Components/schemas section not found"
        success=false
    fi
    
    # Check for proto-level version configuration
    if grep -q "version: 1.0.0" "$sample_yaml"; then
        echo -e "${GREEN}✓${NC} Proto-level version configuration (1.0.0) applied correctly"
    else
        echo -e "${YELLOW}⚠${NC} Expected version 1.0.0 from proto file configuration"
    fi
else
    echo -e "${RED}✗${NC} Sample YAML file not found: $sample_yaml"
    success=false
fi

echo ""
echo "=== Verification Complete ==="
echo ""

if [ "$success" = true ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo ""
    echo "To run validation tests:"
    echo "  sbt 'e2e-nettyJVM2_13/test:testOnly *OpenApiSpecValidationTest'"
    echo "  sbt 'e2e-pekkoJVM2_13/test:testOnly *OpenApiSpecValidationTest'"
    exit 0
else
    echo -e "${RED}✗ Some checks failed${NC}"
    echo ""
    echo "Please review the errors above and ensure:"
    echo "  1. Projects are compiled successfully"
    echo "  2. Proto files have correct annotations"
    echo "  3. build.sbt has correct PB.targets configuration"
    exit 1
fi
