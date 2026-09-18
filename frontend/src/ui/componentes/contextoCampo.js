import { createContext, useContext } from 'react';

// CampoFormulario publica aqui los ids de ayuda y error para que los Campo* se enlacen solos por ARIA.
export const ContextoCampo = createContext(null);

export function usarContextoCampo() {
  const contexto = useContext(ContextoCampo);
  return {
    descrito: contexto ? contexto.descrito : undefined,
    invalido: contexto ? contexto.invalido : false,
  };
}
