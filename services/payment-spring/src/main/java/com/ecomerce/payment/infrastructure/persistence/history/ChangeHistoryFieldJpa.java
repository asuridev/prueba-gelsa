package com.ecomerce.payment.infrastructure.persistence.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Los campos que tocó cada entrada, para el filtro por campo sin operadores JSON del motor (D11): su ruta y el campo de la
 * raíz que la contiene. Mapeada solo para el esquema, como {@link ChangeHistoryJpa}; se va con su entrada (ON DELETE
 * CASCADE).
 */
@Entity
@Table(name = "change_history_field")
@IdClass(ChangeHistoryFieldJpa.Key.class)
public class ChangeHistoryFieldJpa {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, foreignKey = @ForeignKey(name = "fk_change_history_field_entry"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChangeHistoryJpa entry;

    @Id
    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "top", nullable = false, length = 100)
    private String top;

    protected ChangeHistoryFieldJpa() {
    }

    /** La clave compuesta (entry_id, path). */
    public static class Key implements Serializable {

        private UUID entry;
        private String path;

        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(entry, key.entry) && Objects.equals(path, key.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entry, path);
        }
    }
}
