# Pruebas de permisos y comunicacion entre microservicios - GRIP Sneaker Store
# Uso: powershell -ExecutionPolicy Bypass -File pruebas_permisos_grip.ps1
param([string]$Base = "http://18.208.132.235:8080")

function LeerClave($texto) {
    $s = Read-Host $texto -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}

function Token($correo, $clave) {
    $cuerpo = ConvertTo-Json -InputObject @{ correo = $correo; password = $clave }
    $r = Invoke-RestMethod -Uri "$Base/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $cuerpo
    return $r.accessToken
}

function Probar($nombre, $metodo, $ruta, $token, $cuerpo, $esperado) {
    $h = @{}
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $codigo = 0
    $detalle = ""
    try {
        $p = @{ Uri = "$Base$ruta"; Method = $metodo; Headers = $h; UseBasicParsing = $true; ContentType = "application/json" }
        if ($cuerpo -ne $null) { $p["Body"] = (ConvertTo-Json -InputObject $cuerpo -Depth 6) }
        $r = Invoke-WebRequest @p
        $codigo = [int]$r.StatusCode
    } catch {
        if ($_.Exception.Response) {
            $codigo = [int]$_.Exception.Response.StatusCode
            try {
                $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $txt = $sr.ReadToEnd()
                $j = $txt | ConvertFrom-Json
                if ($j.message) { $detalle = $j.message } elseif ($j.error) { $detalle = $j.error }
            } catch { }
        } else {
            $detalle = $_.Exception.Message
        }
    }
    if ($codigo -eq $esperado) { $res = "OK" } else { $res = "REVISAR" }
    return [pscustomobject]@{ Prueba = $nombre; Esperado = $esperado; Obtenido = $codigo; Resultado = $res; Detalle = $detalle }
}

Write-Host "Servidor: $Base" -ForegroundColor Cyan
$claveCliente  = LeerClave "Clave de cliente.prueba@gmail.com"
$claveVendedor = LeerClave "Clave de carlos.soto@duoc.cl"

$tCliente  = Token "cliente.prueba@gmail.com" $claveCliente
$tVendedor = Token "carlos.soto@duoc.cl" $claveVendedor

$sinStock = @{
    cliente = @{ nombre = "Prueba"; apellidos = "Stock"; correo = "cliente.prueba@gmail.com" }
    items   = @( @{ codigo = "SNK-003"; cantidad = 99 } )
    canal   = "online"
    entrega = @{ tipo = "retiro" }
    pago    = @{ metodo = "no_informado" }
}
$productoNuevo = @{ codigo = "SNK-999"; nombre = "Intento cliente"; precio = 1000; stock = 1; stockCritico = 0; categoria = "urbano"; descuento = 0 }

$resultados = @(
    (Probar "1. Catalogo publico sin token"                 "GET"  "/api/v1/productos" $null $null 200),
    (Probar "2. Ordenes sin token"                          "GET"  "/api/v1/ordenes" $null $null 401),
    (Probar "3. Ordenes con token de CLIENTE"               "GET"  "/api/v1/ordenes" $tCliente $null 403),
    (Probar "4. Ordenes con token de VENDEDOR"              "GET"  "/api/v1/ordenes" $tVendedor $null 200),
    (Probar "5. Crear producto con token de CLIENTE"        "POST" "/api/v1/productos" $tCliente $productoNuevo 403),
    (Probar "6. Crear producto con token de VENDEDOR"       "POST" "/api/v1/productos" $tVendedor $productoNuevo 403),
    (Probar "7. Venta en tienda con token de CLIENTE"       "POST" "/api/v1/ordenes" $tCliente $sinStock 403),
    (Probar "8. Token alterado"                             "GET"  "/api/v1/ordenes" ($tVendedor + "x") $null 401),
    (Probar "9. Login con clave incorrecta"                 "POST" "/api/v1/auth/login" $null @{ correo = "carlos.soto@duoc.cl"; password = "claveincorrecta" } 401),
    (Probar "10. Compra de 99 unidades (sin stock)"         "POST" "/api/v1/ordenes/checkout" $null $sinStock 409),
    (Probar "11. API interna por el gateway (bloqueada)"    "POST" "/internal/v1/stock/descontar" $null @() 404)
)

$resultados | Format-Table -AutoSize -Wrap
$archivo = "$env:USERPROFILE\Desktop\resultado_pruebas_permisos_grip.csv"
$resultados | Export-Csv -Path $archivo -NoTypeInformation -Encoding UTF8
$ok = ($resultados | Where-Object { $_.Resultado -eq "OK" }).Count
Write-Host "$ok de $($resultados.Count) pruebas OK. Resultado guardado en: $archivo" -ForegroundColor Green
