import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const RETARDO_ESCRITURA = 350;

function tieneValor(valor) {
  if (valor === null || valor === undefined) return false;
  if (typeof valor === 'string') return valor.trim() !== '';
  return true;
}

export function useFiltros(
  valoresIniciales,
  { clavesConRetardo = [], rangos = [], describir } = {},
) {
  // valores alimenta los controles; al servidor va aplicados, que solo difiere durante
  // el retardo de escritura para no lanzar una petición por tecla.
  const [valores, setValores] = useState(valoresIniciales);
  const [aplicados, setAplicados] = useState(valoresIniciales);

  // En refs para no reconstruir los callbacks en cada render.
  const inicialesRef = useRef(valoresIniciales);
  const conRetardoRef = useRef(clavesConRetardo);
  const rangosRef = useRef(rangos);
  conRetardoRef.current = clavesConRetardo;
  rangosRef.current = rangos;

  useEffect(() => {
    if (valores === aplicados) return undefined;

    const cambioUnCampoEscrito = conRetardoRef.current.some(
      (clave) => valores[clave] !== aplicados[clave],
    );

    if (!cambioUnCampoEscrito) {
      // Desplegables y calendarios se aplican sin retardo.
      setAplicados(valores);
      return undefined;
    }

    const temporizador = setTimeout(() => setAplicados(valores), RETARDO_ESCRITURA);
    return () => clearTimeout(temporizador);
  }, [valores, aplicados]);

  const establecerFiltro = useCallback((clave, valor) => {
    setValores((actuales) =>
      actuales[clave] === valor ? actuales : { ...actuales, [clave]: valor });
  }, []);

  const establecerFiltros = useCallback((parcial) => {
    setValores((actuales) => ({ ...actuales, ...parcial }));
  }, []);

  const quitarFiltro = useCallback((claveOClaves) => {
    const claves = Array.isArray(claveOClaves) ? claveOClaves : [claveOClaves];
    setValores((actuales) => {
      const siguiente = { ...actuales };
      claves.forEach((clave) => {
        siguiente[clave] = inicialesRef.current[clave];
      });
      return siguiente;
    });
  }, []);

  const limpiarFiltros = useCallback(() => {
    setValores(inicialesRef.current);
    setAplicados(inicialesRef.current);
  }, []);

  // Un rango cuenta como un solo filtro y una sola pastilla, aunque la API reciba dos parámetros.
  const grupos = useMemo(() => {
    const listaRangos = rangosRef.current;
    const clavesDeRango = new Set(listaRangos.flatMap((r) => [r.desde, r.hasta]));
    const resultado = [];

    listaRangos.forEach((rango) => {
      const desde = valores[rango.desde];
      const hasta = valores[rango.hasta];
      if (!tieneValor(desde) && !tieneValor(hasta)) return;

      const texto = typeof rango.describir === 'function'
        ? rango.describir(desde, hasta)
        : [desde, hasta].filter(tieneValor).join(' – ');

      resultado.push({
        clave: rango.desde,
        claves: [rango.desde, rango.hasta],
        etiqueta: `${rango.etiqueta}: ${texto}`,
      });
    });

    Object.keys(inicialesRef.current)
      .filter((clave) => !clavesDeRango.has(clave) && tieneValor(valores[clave]))
      .forEach((clave) => {
        const etiqueta = typeof describir === 'function'
          ? describir(clave, valores[clave], valores)
          : `${clave}: ${valores[clave]}`;
        if (etiqueta) resultado.push({ clave, claves: [clave], etiqueta });
      });

    return resultado;
  }, [valores, describir]);

  const chips = useMemo(
    () => grupos.map((grupo) => ({
      clave: grupo.clave,
      etiqueta: grupo.etiqueta,
      onQuitar: () => quitarFiltro(grupo.claves),
    })),
    [grupos, quitarFiltro],
  );

  return {
    valores,
    aplicados,
    establecerFiltro,
    establecerFiltros,
    quitarFiltro,
    limpiarFiltros,
    filtrosActivos: grupos.length,
    chips,
  };
}

export default useFiltros;
