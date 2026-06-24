$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$port = if ($args.Count -gt 0) { $args[0] } else { "5173" }

python -m http.server $port --bind 0.0.0.0 --directory $scriptDir
