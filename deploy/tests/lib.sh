# Tiny assert helpers sourced by every test-*.sh.
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

fail() { echo "FAIL: $*" >&2; exit 1; }
assert_eq() { [[ "$1" == "$2" ]] || fail "$3: expected [$2], got [$1]"; }
assert_contains() { [[ "$1" == *"$2"* ]] || fail "$3: [$2] not found in [$1]"; }
assert_not_contains() { [[ "$1" != *"$2"* ]] || fail "$3: [$2] unexpectedly found in [$1]"; }
