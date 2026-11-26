--
-- PostgreSQL database dump
--

\restrict RPjzbUxZZJuBgfEHBfcoVOeFTOaUlNeYWZQzVMldv15KSXWacbEPN6dxPexarVY

-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: core; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA core;


--
-- Name: fin; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA fin;


--
-- Name: hr; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA hr;


--
-- Name: inv; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA inv;


--
-- Name: md; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA md;


--
-- Name: mf; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA mf;


--
-- Name: pos; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA pos;


--
-- Name: pur; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA pur;


--
-- Name: rpt; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA rpt;


--
-- Name: sal; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sal;


--
-- Name: sys; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA sys;


--
-- Name: btree_gin; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gin WITH SCHEMA public;


--
-- Name: EXTENSION btree_gin; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gin IS 'support for indexing common datatypes in GIN';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;


--
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: core; Owner: -
--

CREATE FUNCTION core.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.app_user (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    username character varying(64) NOT NULL,
    email character varying(255),
    password_hash text NOT NULL,
    full_name character varying(200),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    must_change_pwd boolean DEFAULT false NOT NULL,
    failed_attempts smallint DEFAULT 0 NOT NULL,
    locked_until timestamp with time zone,
    last_login timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    employee_id bigint,
    default_company_id bigint,
    CONSTRAINT app_user_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOCKED'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.app_user ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: approval_log; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.approval_log (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    doc_type character varying(30) NOT NULL,
    doc_id bigint NOT NULL,
    level integer NOT NULL,
    approver_username character varying(100),
    approver_role character varying(50),
    action character varying(20) NOT NULL,
    amount numeric(18,4),
    remarks text,
    action_date timestamp with time zone DEFAULT now()
);


--
-- Name: approval_log_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.approval_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_log_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.approval_log_id_seq OWNED BY core.approval_log.id;


--
-- Name: approval_matrix; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.approval_matrix (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    doc_type character varying(30) NOT NULL,
    level integer NOT NULL,
    min_amount numeric(18,4) DEFAULT 0 NOT NULL,
    max_amount numeric(18,4) DEFAULT '999999999999'::bigint NOT NULL,
    role_required character varying(50) NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: approval_matrix_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.approval_matrix_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: approval_matrix_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.approval_matrix_id_seq OWNED BY core.approval_matrix.id;


--
-- Name: audit_log; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.audit_log (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint,
    user_id bigint,
    schema_name character varying(20) NOT NULL,
    table_name character varying(64) NOT NULL,
    record_id text NOT NULL,
    action character varying(10) NOT NULL,
    old_data jsonb,
    new_data jsonb,
    remote_ip inet,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT audit_log_action_check CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
)
PARTITION BY RANGE (created_at);


--
-- Name: audit_log_2026_11; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.audit_log_2026_11 (
    id bigint CONSTRAINT audit_log_id_not_null NOT NULL,
    tenant_id bigint CONSTRAINT audit_log_tenant_id_not_null NOT NULL,
    company_id bigint,
    user_id bigint,
    schema_name character varying(20) CONSTRAINT audit_log_schema_name_not_null NOT NULL,
    table_name character varying(64) CONSTRAINT audit_log_table_name_not_null NOT NULL,
    record_id text CONSTRAINT audit_log_record_id_not_null NOT NULL,
    action character varying(10) CONSTRAINT audit_log_action_not_null NOT NULL,
    old_data jsonb,
    new_data jsonb,
    remote_ip inet,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT audit_log_created_at_not_null NOT NULL,
    CONSTRAINT audit_log_action_check CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: audit_log_2026_12; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.audit_log_2026_12 (
    id bigint CONSTRAINT audit_log_id_not_null NOT NULL,
    tenant_id bigint CONSTRAINT audit_log_tenant_id_not_null NOT NULL,
    company_id bigint,
    user_id bigint,
    schema_name character varying(20) CONSTRAINT audit_log_schema_name_not_null NOT NULL,
    table_name character varying(64) CONSTRAINT audit_log_table_name_not_null NOT NULL,
    record_id text CONSTRAINT audit_log_record_id_not_null NOT NULL,
    action character varying(10) CONSTRAINT audit_log_action_not_null NOT NULL,
    old_data jsonb,
    new_data jsonb,
    remote_ip inet,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT audit_log_created_at_not_null NOT NULL,
    CONSTRAINT audit_log_action_check CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: audit_log_2027_01; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.audit_log_2027_01 (
    id bigint CONSTRAINT audit_log_id_not_null NOT NULL,
    tenant_id bigint CONSTRAINT audit_log_tenant_id_not_null NOT NULL,
    company_id bigint,
    user_id bigint,
    schema_name character varying(20) CONSTRAINT audit_log_schema_name_not_null NOT NULL,
    table_name character varying(64) CONSTRAINT audit_log_table_name_not_null NOT NULL,
    record_id text CONSTRAINT audit_log_record_id_not_null NOT NULL,
    action character varying(10) CONSTRAINT audit_log_action_not_null NOT NULL,
    old_data jsonb,
    new_data jsonb,
    remote_ip inet,
    created_at timestamp with time zone DEFAULT now() CONSTRAINT audit_log_created_at_not_null NOT NULL,
    CONSTRAINT audit_log_action_check CHECK (((action)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.audit_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: company; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.company (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    tax_id character varying(50),
    base_currency character(3) DEFAULT 'IDR'::bpchar NOT NULL,
    timezone character varying(50) DEFAULT 'Asia/Jakarta'::character varying NOT NULL,
    fiscal_year_start_month smallint DEFAULT 1 NOT NULL,
    parent_id bigint,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    country_code character(2) DEFAULT 'ID'::bpchar NOT NULL,
    local_currency character(3) DEFAULT 'IDR'::bpchar NOT NULL,
    chart_of_accounts_id bigint,
    address text,
    phone character varying(50),
    email character varying(255),
    CONSTRAINT company_fiscal_year_start_month_check CHECK (((fiscal_year_start_month >= 1) AND (fiscal_year_start_month <= 12))),
    CONSTRAINT company_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: company_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.company ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.company_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: menu; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.menu (
    id bigint NOT NULL,
    parent_id bigint,
    code character varying(80) NOT NULL,
    name character varying(200) NOT NULL,
    path character varying(255),
    icon character varying(100),
    sort_order integer DEFAULT 0 NOT NULL,
    is_header boolean DEFAULT false NOT NULL,
    required_permission character varying(80),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT menu_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: menu_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.menu ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME core.menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: number_range; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.number_range (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    doc_type character varying(50) NOT NULL,
    prefix character varying(20) NOT NULL,
    period character varying(6) NOT NULL,
    current_number integer DEFAULT 0 NOT NULL
);


--
-- Name: number_range_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

CREATE SEQUENCE core.number_range_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: number_range_id_seq; Type: SEQUENCE OWNED BY; Schema: core; Owner: -
--

ALTER SEQUENCE core.number_range_id_seq OWNED BY core.number_range.id;


--
-- Name: permission; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.permission (
    id bigint NOT NULL,
    code character varying(80) NOT NULL,
    module character varying(30) NOT NULL,
    description text
);


--
-- Name: permission_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.permission ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: refresh_token; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.refresh_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(128) NOT NULL,
    family_id uuid NOT NULL,
    user_agent character varying(255),
    ip_address character varying(45),
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    revoked_reason character varying(50),
    replaced_by_hash character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: refresh_token_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.refresh_token ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.refresh_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: role; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.role (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: role_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.role ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: role_permission; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


--
-- Name: tenant; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.tenant (
    id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tenant_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: tenant_id_seq; Type: SEQUENCE; Schema: core; Owner: -
--

ALTER TABLE core.tenant ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME core.tenant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_company; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.user_company (
    user_id bigint NOT NULL,
    company_id bigint NOT NULL
);


--
-- Name: user_role; Type: TABLE; Schema: core; Owner: -
--

CREATE TABLE core.user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: ap_payment; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.ap_payment (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    supplier_id bigint NOT NULL,
    bank_id bigint NOT NULL,
    amount numeric(18,4) NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying
);


--
-- Name: ap_payment_dtl; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.ap_payment_dtl (
    id bigint NOT NULL,
    payment_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount numeric(18,4) DEFAULT 0 NOT NULL,
    remarks text
);


--
-- Name: ap_payment_dtl_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.ap_payment_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ap_payment_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.ap_payment_dtl_id_seq OWNED BY fin.ap_payment_dtl.id;


--
-- Name: ap_payment_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.ap_payment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.ap_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ar_collection; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.ar_collection (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    customer_id bigint NOT NULL,
    bank_id bigint NOT NULL,
    amount numeric(18,4) NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying
);


--
-- Name: ar_collection_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.ar_collection ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.ar_collection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ar_receipt_dtl; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.ar_receipt_dtl (
    id bigint NOT NULL,
    receipt_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    amount numeric(18,4) DEFAULT 0 NOT NULL,
    remarks text
);


--
-- Name: ar_receipt_dtl_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.ar_receipt_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ar_receipt_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.ar_receipt_dtl_id_seq OWNED BY fin.ar_receipt_dtl.id;


--
-- Name: asset_depreciation_log; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.asset_depreciation_log (
    id bigint NOT NULL,
    asset_id bigint NOT NULL,
    period character varying(7) NOT NULL,
    depreciation_amount numeric(18,4) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(16) DEFAULT 'POSTED'::character varying,
    CONSTRAINT asset_depreciation_log_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'POSTED'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: asset_depreciation_log_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.asset_depreciation_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.asset_depreciation_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: asset_master; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.asset_master (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    asset_code character varying(50) NOT NULL,
    asset_name character varying(200) NOT NULL,
    category character varying(50) NOT NULL,
    acquisition_date date NOT NULL,
    acquisition_cost numeric(18,4) NOT NULL,
    useful_life_years integer NOT NULL,
    salvage_value numeric(18,4) DEFAULT 0,
    depreciation_method character varying(20) DEFAULT 'STRAIGHT_LINE'::character varying,
    accumulated_depreciation numeric(18,4) DEFAULT 0,
    book_value numeric(18,4) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: asset_master_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.asset_master ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.asset_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bank_account; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.bank_account (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    account_no character varying(50),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    coa_id bigint,
    branch character varying(100),
    currency character varying(3) DEFAULT 'IDR'::character varying,
    opening_balance numeric(19,2) DEFAULT 0,
    current_balance numeric(19,2) DEFAULT 0,
    bank_code character varying(20)
);


--
-- Name: bank_account_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.bank_account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.bank_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cash_bank_trx; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.cash_bank_trx (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    trx_type character varying(10) NOT NULL,
    bank_id bigint NOT NULL,
    amount numeric(18,4) NOT NULL,
    remarks text,
    status character varying(16) DEFAULT 'POSTED'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    supplier_id bigint,
    customer_id bigint,
    payment_method character varying(20) DEFAULT 'TRANSFER'::character varying,
    ref_no character varying(50),
    check_no character varying(50),
    check_date date,
    CONSTRAINT cash_bank_trx_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'POSTED'::character varying, 'VOID'::character varying])::text[]))),
    CONSTRAINT cash_bank_trx_trx_type_check CHECK (((trx_type)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))
);


--
-- Name: cash_bank_trx_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.cash_bank_trx ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.cash_bank_trx_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: chart_of_account; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.chart_of_account (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(200) NOT NULL,
    account_type character varying(16) NOT NULL,
    parent_id bigint,
    is_header boolean DEFAULT false NOT NULL,
    currency character(3),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    normal_balance character varying(10) DEFAULT 'DEBIT'::character varying,
    is_reconciliation boolean DEFAULT false,
    is_bank_account boolean DEFAULT false,
    tax_code character varying(20),
    is_active boolean DEFAULT true,
    description text,
    level integer DEFAULT 1,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chart_of_account_account_type_check CHECK (((account_type)::text = ANY ((ARRAY['ASSET'::character varying, 'LIABILITY'::character varying, 'EQUITY'::character varying, 'REVENUE'::character varying, 'EXPENSE'::character varying])::text[]))),
    CONSTRAINT chart_of_account_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: chart_of_account_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.chart_of_account ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.chart_of_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cost_center; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.cost_center (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    parent_id bigint,
    manager_id bigint,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: cost_center_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.cost_center_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cost_center_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.cost_center_id_seq OWNED BY fin.cost_center.id;


--
-- Name: fiscal_period; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.fiscal_period (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    year integer NOT NULL,
    month integer NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) DEFAULT 'OPEN'::character varying,
    closed_at timestamp with time zone,
    closed_by bigint,
    reopened_at timestamp with time zone,
    reopened_by bigint,
    period character varying(7) NOT NULL,
    opened_at timestamp with time zone DEFAULT now() NOT NULL,
    opened_by character varying(100),
    locked_at timestamp with time zone,
    locked_by character varying(100),
    remarks text,
    CONSTRAINT ck_fin_fiscal_period_dates CHECK (((start_date <= end_date) AND ((period)::text = (((year)::text || '-'::text) || lpad((month)::text, 2, '0'::text))))),
    CONSTRAINT fiscal_period_month_check CHECK (((month >= 1) AND (month <= 12)))
);


--
-- Name: fiscal_period_audit; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.fiscal_period_audit (
    id bigint NOT NULL,
    fiscal_period_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    period character varying(7) NOT NULL,
    action character varying(20) NOT NULL,
    old_status character varying(10),
    new_status character varying(10) NOT NULL,
    reason text NOT NULL,
    performed_by character varying(100) NOT NULL,
    performed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT fiscal_period_audit_action_check CHECK (((action)::text = ANY ((ARRAY['GENERATE'::character varying, 'CLOSE'::character varying, 'REOPEN'::character varying, 'LOCK'::character varying])::text[])))
);


--
-- Name: fiscal_period_audit_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.fiscal_period_audit ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME fin.fiscal_period_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: fiscal_period_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.fiscal_period_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fiscal_period_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.fiscal_period_id_seq OWNED BY fin.fiscal_period.id;


--
-- Name: fixed_asset; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.fixed_asset (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    asset_code character varying(50) NOT NULL,
    asset_name character varying(200) NOT NULL,
    acquisition_date date NOT NULL,
    acquisition_cost numeric(19,4) NOT NULL,
    useful_life_months integer NOT NULL,
    salvage_value numeric(19,4) DEFAULT 0,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: fixed_asset_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.fixed_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fixed_asset_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.fixed_asset_id_seq OWNED BY fin.fixed_asset.id;


--
-- Name: investment; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.investment (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    acquisition_date date NOT NULL,
    book_value numeric(18,4) NOT NULL,
    last_fair_value numeric(18,4) NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: investment_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.investment ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.investment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: journal_dtl; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.journal_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    account_id bigint NOT NULL,
    debit numeric(18,4) DEFAULT 0 NOT NULL,
    credit numeric(18,4) DEFAULT 0 NOT NULL,
    description character varying(255)
);


--
-- Name: journal_dtl_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.journal_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.journal_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: journal_hdr; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.journal_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(30) NOT NULL,
    doc_date date NOT NULL,
    journal_type character varying(30) NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(20) DEFAULT 'POSTED'::character varying,
    ref_type character varying(30),
    ref_id bigint
);


--
-- Name: journal_hdr_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.journal_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.journal_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notes; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.notes (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    note_type character varying(10) NOT NULL,
    partner_id bigint NOT NULL,
    ref_invoice_id bigint,
    total_amount numeric(18,4) NOT NULL,
    tax_amount numeric(18,4) DEFAULT 0,
    remarks text,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT notes_note_type_check CHECK (((note_type)::text = ANY ((ARRAY['AR_DN'::character varying, 'AR_CN'::character varying, 'AP_DN'::character varying, 'AP_CN'::character varying])::text[])))
);


--
-- Name: notes_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

ALTER TABLE fin.notes ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME fin.notes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: payment_dtl; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.payment_dtl (
    id bigint NOT NULL,
    header_id bigint,
    invoice_id bigint,
    amount numeric(19,4)
);


--
-- Name: payment_dtl_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.payment_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.payment_dtl_id_seq OWNED BY fin.payment_dtl.id;


--
-- Name: payment_hdr; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.payment_hdr (
    id bigint NOT NULL,
    tenant_id bigint,
    company_id bigint,
    doc_no character varying(50),
    doc_date date,
    payment_type character varying(20),
    partner_id bigint,
    total_amount numeric(19,4) DEFAULT 0,
    status character varying(20),
    remarks character varying(255)
);


--
-- Name: payment_hdr_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.payment_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_hdr_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.payment_hdr_id_seq OWNED BY fin.payment_hdr.id;


--
-- Name: payment_method; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.payment_method (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    account_id bigint,
    is_active boolean DEFAULT true
);


--
-- Name: payment_method_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.payment_method_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_method_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.payment_method_id_seq OWNED BY fin.payment_method.id;


--
-- Name: profit_center; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.profit_center (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    parent_id bigint,
    manager_id bigint,
    is_active boolean DEFAULT true
);


--
-- Name: profit_center_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.profit_center_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: profit_center_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.profit_center_id_seq OWNED BY fin.profit_center.id;


--
-- Name: purchase_order; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.purchase_order (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    po_number character varying(30) NOT NULL,
    supplier_id bigint NOT NULL,
    pr_id bigint,
    po_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    bypass_reason text,
    bypass_approved_by bigint,
    order_date date NOT NULL,
    total_amount numeric(18,2) DEFAULT 0,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: purchase_order_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.purchase_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_order_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.purchase_order_id_seq OWNED BY fin.purchase_order.id;


--
-- Name: purchase_policy; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.purchase_policy (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    rule_type character varying(30) NOT NULL,
    threshold_value numeric(18,2),
    reference_id bigint,
    requires_pr boolean NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: purchase_policy_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.purchase_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.purchase_policy_id_seq OWNED BY fin.purchase_policy.id;


--
-- Name: purchase_requisition; Type: TABLE; Schema: fin; Owner: -
--

CREATE TABLE fin.purchase_requisition (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    pr_number character varying(30) NOT NULL,
    requester_id bigint NOT NULL,
    department_id bigint,
    request_date date NOT NULL,
    needed_date date,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    total_amount numeric(18,2) DEFAULT 0,
    notes text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: purchase_requisition_id_seq; Type: SEQUENCE; Schema: fin; Owner: -
--

CREATE SEQUENCE fin.purchase_requisition_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: purchase_requisition_id_seq; Type: SEQUENCE OWNED BY; Schema: fin; Owner: -
--

ALTER SEQUENCE fin.purchase_requisition_id_seq OWNED BY fin.purchase_requisition.id;


--
-- Name: attendance; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.attendance (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    att_date date NOT NULL,
    status character varying(16) NOT NULL,
    late_minutes integer DEFAULT 0,
    remarks text,
    CONSTRAINT attendance_status_check CHECK (((status)::text = ANY ((ARRAY['HADIR'::character varying, 'SAKIT'::character varying, 'IZIN'::character varying, 'ALPHA'::character varying, 'CUTI'::character varying])::text[])))
);


--
-- Name: attendance_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.attendance ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.attendance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cash_advance; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.cash_advance (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    employee_id bigint NOT NULL,
    bank_id bigint NOT NULL,
    amount numeric(18,4) NOT NULL,
    remarks text,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expense_amount numeric(18,4) DEFAULT 0,
    return_amount numeric(18,4) DEFAULT 0,
    settlement_date date,
    CONSTRAINT cash_advance_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'POSTED'::character varying, 'SETTLED'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: cash_advance_expense; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.cash_advance_expense (
    id bigint NOT NULL,
    cash_advance_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    amount numeric(19,2) NOT NULL
);


--
-- Name: cash_advance_expense_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.cash_advance_expense ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.cash_advance_expense_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: cash_advance_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.cash_advance ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.cash_advance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: department; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.department (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    parent_id bigint,
    manager_id bigint,
    cost_center_id bigint,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: department_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

CREATE SEQUENCE hr.department_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: hr; Owner: -
--

ALTER SEQUENCE hr.department_id_seq OWNED BY hr.department.id;


--
-- Name: employee; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.employee (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    nip character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    department character varying(50),
    "position" character varying(50),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    npwp character varying(30),
    nik character varying(20),
    birth_date date,
    gender character varying(10),
    marital_status character varying(20),
    dependent_count integer DEFAULT 0,
    ptkp_status character varying(10) DEFAULT 'TK/0'::character varying,
    employment_type character varying(20) DEFAULT 'PKWTT'::character varying,
    join_date date,
    termination_date date,
    bank_name character varying(100),
    bank_account_no character varying(50),
    bank_account_name character varying(100),
    bpjs_kesehatan_no character varying(30),
    bpjs_tk_no character varying(30),
    department_id bigint,
    position_id bigint,
    cost_center_id bigint,
    supervisor_id bigint,
    phone character varying(20),
    email character varying(100),
    address text,
    photo_url text
);


--
-- Name: employee_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.employee ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.employee_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: employee_salary; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.employee_salary (
    employee_id bigint NOT NULL,
    base_salary numeric(18,4) DEFAULT 0 NOT NULL,
    fixed_allowance numeric(18,4) DEFAULT 0 NOT NULL,
    bpjs_kesehatan_rate numeric(5,2) DEFAULT 1,
    bpjs_jht_rate numeric(5,2) DEFAULT 2,
    bpjs_jp_rate numeric(5,2) DEFAULT 1,
    ptkp_status character varying(10) DEFAULT 'TK/0'::character varying,
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: payroll_dtl; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.payroll_dtl (
    id bigint NOT NULL,
    payroll_run_id bigint NOT NULL,
    employee_id bigint NOT NULL,
    gross_salary numeric(18,4) NOT NULL,
    bpjs_deduction numeric(18,4) DEFAULT 0 NOT NULL,
    pph21_deduction numeric(18,4) DEFAULT 0 NOT NULL,
    net_salary numeric(18,4) NOT NULL
);


--
-- Name: payroll_dtl_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.payroll_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.payroll_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: payroll_run; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr.payroll_run (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    period character varying(7) NOT NULL,
    total_gross numeric(18,4) DEFAULT 0 NOT NULL,
    total_deduction numeric(18,4) DEFAULT 0 NOT NULL,
    total_net numeric(18,4) DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT payroll_run_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'POSTED'::character varying])::text[])))
);


--
-- Name: payroll_run_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

ALTER TABLE hr.payroll_run ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME hr.payroll_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: position; Type: TABLE; Schema: hr; Owner: -
--

CREATE TABLE hr."position" (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    level integer DEFAULT 1,
    department_id bigint,
    min_salary numeric(19,2),
    max_salary numeric(19,2),
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: position_id_seq; Type: SEQUENCE; Schema: hr; Owner: -
--

CREATE SEQUENCE hr.position_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: position_id_seq; Type: SEQUENCE OWNED BY; Schema: hr; Owner: -
--

ALTER SEQUENCE hr.position_id_seq OWNED BY hr."position".id;


--
-- Name: inventory_period_control; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.inventory_period_control (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    period character varying(7) NOT NULL,
    status character varying(10) DEFAULT 'OPEN'::character varying NOT NULL,
    opened_at timestamp with time zone DEFAULT now() NOT NULL,
    closed_at timestamp with time zone,
    closed_by character varying(100),
    remarks text,
    CONSTRAINT inventory_period_control_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: inventory_period_control_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

ALTER TABLE inv.inventory_period_control ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME inv.inventory_period_control_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: stock_balance; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_balance (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    item_id bigint NOT NULL,
    warehouse_id bigint DEFAULT 1 NOT NULL,
    qty numeric(18,4) DEFAULT 0 NOT NULL,
    avg_cost numeric(18,4) DEFAULT 0 NOT NULL,
    total_value numeric(18,4) DEFAULT 0 NOT NULL,
    last_updated timestamp with time zone DEFAULT now(),
    qty_base numeric(19,4) DEFAULT 0
);


--
-- Name: stock_balance_backup_20261109; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_balance_backup_20261109 (
    warehouse_id bigint,
    item_id bigint,
    qty_on_hand numeric(18,4),
    last_updated timestamp with time zone
);


--
-- Name: stock_balance_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_balance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_balance_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_balance_id_seq OWNED BY inv.stock_balance.id;


--
-- Name: stock_movement; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_movement (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    movement_date date NOT NULL,
    item_id bigint NOT NULL,
    warehouse_id bigint DEFAULT 1 NOT NULL,
    direction character varying(3) NOT NULL,
    qty numeric(18,4) NOT NULL,
    unit_cost numeric(18,4) NOT NULL,
    total_cost numeric(18,4) NOT NULL,
    balance_qty numeric(18,4) NOT NULL,
    balance_avg_cost numeric(18,4) NOT NULL,
    reference_type character varying(30) NOT NULL,
    reference_id bigint,
    reference_no character varying(50),
    remarks text,
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT stock_movement_direction_check CHECK (((direction)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying])::text[])))
);


--
-- Name: stock_movement_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_movement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_movement_id_seq OWNED BY inv.stock_movement.id;


--
-- Name: stock_opname_dtl; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_opname_dtl (
    id bigint NOT NULL,
    header_id bigint,
    item_id bigint,
    uom_id bigint,
    system_qty numeric(19,4) DEFAULT 0,
    physical_qty numeric(19,4) DEFAULT 0,
    diff_qty numeric(19,4) DEFAULT 0
);


--
-- Name: stock_opname_dtl_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_opname_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_opname_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_opname_dtl_id_seq OWNED BY inv.stock_opname_dtl.id;


--
-- Name: stock_opname_hdr; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_opname_hdr (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1,
    company_id bigint DEFAULT 1,
    doc_no character varying(50),
    doc_date date,
    warehouse_id bigint,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    remarks character varying(255)
);


--
-- Name: stock_opname_hdr_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_opname_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_opname_hdr_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_opname_hdr_id_seq OWNED BY inv.stock_opname_hdr.id;


--
-- Name: stock_period_snapshot; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_period_snapshot (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    warehouse_id bigint DEFAULT 1 NOT NULL,
    item_id bigint NOT NULL,
    period character varying(7) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    begin_qty numeric(18,4) DEFAULT 0 NOT NULL,
    begin_avg_cost numeric(18,4) DEFAULT 0 NOT NULL,
    begin_value numeric(18,4) DEFAULT 0 NOT NULL,
    in_qty numeric(18,4) DEFAULT 0 NOT NULL,
    in_value numeric(18,4) DEFAULT 0 NOT NULL,
    out_qty numeric(18,4) DEFAULT 0 NOT NULL,
    out_value numeric(18,4) DEFAULT 0 NOT NULL,
    end_qty numeric(18,4) DEFAULT 0 NOT NULL,
    end_avg_cost numeric(18,4) DEFAULT 0 NOT NULL,
    end_value numeric(18,4) DEFAULT 0 NOT NULL,
    cogs numeric(18,4) DEFAULT 0 NOT NULL,
    recalculated_at timestamp with time zone DEFAULT now()
);


--
-- Name: stock_period_snapshot_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_period_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_period_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_period_snapshot_id_seq OWNED BY inv.stock_period_snapshot.id;


--
-- Name: stock_transfer_dtl; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_transfer_dtl (
    id bigint NOT NULL,
    header_id bigint,
    item_id bigint,
    uom_id bigint,
    qty numeric(19,4)
);


--
-- Name: stock_transfer_dtl_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_transfer_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_transfer_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_transfer_dtl_id_seq OWNED BY inv.stock_transfer_dtl.id;


--
-- Name: stock_transfer_hdr; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.stock_transfer_hdr (
    id bigint NOT NULL,
    tenant_id bigint,
    doc_no character varying(100),
    transfer_date date,
    from_warehouse_id bigint,
    to_warehouse_id bigint,
    status character varying(50),
    remarks character varying(255)
);


--
-- Name: stock_transfer_hdr_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

CREATE SEQUENCE inv.stock_transfer_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_transfer_hdr_id_seq; Type: SEQUENCE OWNED BY; Schema: inv; Owner: -
--

ALTER SEQUENCE inv.stock_transfer_hdr_id_seq OWNED BY inv.stock_transfer_hdr.id;


--
-- Name: transaction_detail; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.transaction_detail (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    source_do_detail_id bigint,
    reversal_of_detail_id bigint,
    stock_movement_id bigint,
    CONSTRAINT transaction_detail_qty_check CHECK ((qty > (0)::numeric))
);


--
-- Name: transaction_detail_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

ALTER TABLE inv.transaction_detail ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME inv.transaction_detail_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: transaction_header; Type: TABLE; Schema: inv; Owner: -
--

CREATE TABLE inv.transaction_header (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_type character varying(10) NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    warehouse_id bigint NOT NULL,
    remarks text,
    status character varying(16) DEFAULT 'POSTED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    source_do_id bigint,
    idempotency_key character varying(80),
    reversal_of_header_id bigint,
    CONSTRAINT transaction_header_doc_type_check CHECK (((doc_type)::text = ANY ((ARRAY['IN'::character varying, 'OUT'::character varying, 'ADJ'::character varying])::text[]))),
    CONSTRAINT transaction_header_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'POSTED'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: transaction_header_id_seq; Type: SEQUENCE; Schema: inv; Owner: -
--

ALTER TABLE inv.transaction_header ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME inv.transaction_header_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bom_detail; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.bom_detail (
    id bigint NOT NULL,
    bom_header_id bigint NOT NULL,
    material_item_id bigint NOT NULL,
    qty numeric(19,4) NOT NULL,
    uom_id bigint
);


--
-- Name: bom_detail_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.bom_detail_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bom_detail_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.bom_detail_id_seq OWNED BY md.bom_detail.id;


--
-- Name: bom_header; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.bom_header (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    product_item_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    version character varying(20) DEFAULT '1.0'::character varying,
    qty_output numeric(19,4) DEFAULT 1,
    is_active boolean DEFAULT true
);


--
-- Name: bom_header_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.bom_header_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bom_header_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.bom_header_id_seq OWNED BY md.bom_header.id;


--
-- Name: company; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.company (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    tax_id character varying(50),
    address text,
    phone character varying(50),
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: company_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.company_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: company_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.company_id_seq OWNED BY md.company.id;


--
-- Name: currency; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.currency (
    code character(3) NOT NULL,
    name character varying(100) NOT NULL,
    symbol character varying(10),
    decimal_places smallint DEFAULT 2 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: customer; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.customer (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    tax_id character varying(50),
    currency character(3),
    credit_limit numeric(18,4) DEFAULT 0 NOT NULL,
    payment_term_days smallint DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    npwp character varying(30),
    nitku character varying(30),
    is_pkp boolean DEFAULT true,
    default_tax_id bigint,
    payment_term_id bigint,
    credit_used numeric(19,2) DEFAULT 0,
    customer_group character varying(20) DEFAULT 'SOLD_TO'::character varying,
    salesperson_id bigint,
    price_list_id bigint,
    contact_person character varying(100),
    phone character varying(50),
    email character varying(100),
    billing_address text,
    shipping_address text,
    city character varying(50),
    postal_code character varying(10),
    ar_account_id bigint,
    CONSTRAINT customer_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'BLOCKED'::character varying])::text[])))
);


--
-- Name: customer_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.customer ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.customer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: djp_goods_code; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.djp_goods_code (
    code character varying(10) NOT NULL,
    name character varying(255) NOT NULL,
    description text
);


--
-- Name: driver; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.driver (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    name character varying(100) NOT NULL,
    phone character varying(20),
    license_no character varying(50),
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: driver_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.driver_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: driver_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.driver_id_seq OWNED BY md.driver.id;


--
-- Name: exchange_rate; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.exchange_rate (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    from_currency character(3) NOT NULL,
    to_currency character(3) NOT NULL,
    rate numeric(18,8) NOT NULL,
    effective_date date NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT exchange_rate_rate_check CHECK ((rate > (0)::numeric))
);


--
-- Name: exchange_rate_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.exchange_rate ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.exchange_rate_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: item; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.item (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category_id bigint,
    base_uom_id bigint NOT NULL,
    item_type character varying(16) DEFAULT 'STOCK'::character varying NOT NULL,
    track_lot boolean DEFAULT false NOT NULL,
    track_serial boolean DEFAULT false NOT NULL,
    barcode character varying(50),
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    purchasing_group character varying(20) DEFAULT '001'::character varying,
    lead_time_days integer DEFAULT 0,
    price_control character(1) DEFAULT 'V'::bpchar,
    valuation_class character varying(20) DEFAULT '3000'::character varying,
    standard_price numeric(18,4) DEFAULT 0,
    djp_goods_code character varying(10) DEFAULT '000000'::character varying,
    min_stock numeric(19,4) DEFAULT 0,
    reorder_point numeric(19,4) DEFAULT 0,
    purchase_tax_id bigint,
    sales_tax_id bigint,
    inventory_account_id bigint,
    cogs_account_id bigint,
    sales_account_id bigint,
    is_purchasable boolean DEFAULT true,
    is_sellable boolean DEFAULT true,
    is_stock_item boolean DEFAULT true,
    safety_stock numeric(19,4) DEFAULT 0,
    max_stock numeric(19,4) DEFAULT 0,
    hs_code character varying(20),
    image_url text,
    weight_kg numeric(10,3),
    volume_m3 numeric(10,4),
    uom_set_id bigint,
    CONSTRAINT item_item_type_check CHECK (((item_type)::text = ANY ((ARRAY['STOCK'::character varying, 'NONSTOCK'::character varying, 'SERVICE'::character varying])::text[]))),
    CONSTRAINT item_price_control_check CHECK ((price_control = ANY (ARRAY['S'::bpchar, 'V'::bpchar]))),
    CONSTRAINT item_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: item_batch; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.item_batch (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    item_id bigint NOT NULL,
    warehouse_id bigint,
    batch_no character varying(50) NOT NULL,
    manufacture_date date,
    expiry_date date,
    qty_on_hand numeric(19,4) DEFAULT 0
);


--
-- Name: item_batch_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.item_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_batch_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.item_batch_id_seq OWNED BY md.item_batch.id;


--
-- Name: item_category; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.item_category (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(200) NOT NULL,
    parent_id bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: item_category_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.item_category ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.item_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: item_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.item ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: item_price; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.item_price (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    item_id bigint NOT NULL,
    currency character(3) NOT NULL,
    price numeric(18,4) NOT NULL,
    valid_from date NOT NULL,
    valid_to date,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    uom_id bigint NOT NULL,
    CONSTRAINT item_price_check CHECK (((valid_to IS NULL) OR (valid_to > valid_from))),
    CONSTRAINT item_price_price_check CHECK ((price >= (0)::numeric)),
    CONSTRAINT item_price_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: item_price_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.item_price ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.item_price_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: item_serial; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.item_serial (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    item_id bigint NOT NULL,
    serial_no character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'AVAILABLE'::character varying,
    purchase_date date,
    purchase_price numeric(19,2),
    warranty_until date
);


--
-- Name: item_serial_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.item_serial_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_serial_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.item_serial_id_seq OWNED BY md.item_serial.id;


--
-- Name: payment_term; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.payment_term (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    days integer DEFAULT 0 NOT NULL,
    description text,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: payment_term_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.payment_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payment_term_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.payment_term_id_seq OWNED BY md.payment_term.id;


--
-- Name: salesperson; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.salesperson (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    employee_id bigint,
    commission_pct numeric(5,2) DEFAULT 0,
    phone character varying(20),
    email character varying(100),
    is_active boolean DEFAULT true
);


--
-- Name: salesperson_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.salesperson_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salesperson_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.salesperson_id_seq OWNED BY md.salesperson.id;


--
-- Name: shipping_method; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.shipping_method (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    is_active boolean DEFAULT true
);


--
-- Name: shipping_method_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.shipping_method_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shipping_method_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.shipping_method_id_seq OWNED BY md.shipping_method.id;


--
-- Name: supplier; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.supplier (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    tax_id character varying(50),
    currency character(3),
    payment_term_days smallint DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    npwp character varying(30),
    supplier_group character varying(20) DEFAULT 'VENDOR'::character varying,
    default_tax_id bigint,
    pph_type character varying(20) DEFAULT 'NONE'::character varying,
    pph_rate numeric(5,2) DEFAULT 0,
    bank_name character varying(100),
    bank_account_no character varying(50),
    bank_account_name character varying(100),
    contact_person character varying(100),
    phone character varying(50),
    email character varying(100),
    address text,
    city character varying(50),
    ap_account_id bigint,
    CONSTRAINT supplier_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'BLOCKED'::character varying])::text[])))
);


--
-- Name: supplier_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.supplier ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.supplier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tax; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.tax (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    rate numeric(8,4) NOT NULL,
    tax_type character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    gl_account_id bigint,
    is_sales boolean DEFAULT false,
    is_purchase boolean DEFAULT false,
    CONSTRAINT tax_rate_check CHECK ((rate >= (0)::numeric)),
    CONSTRAINT tax_tax_type_check CHECK (((tax_type)::text = ANY ((ARRAY['VAT'::character varying, 'WHT'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: tax_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.tax ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.tax_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: uom; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.uom (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: uom_conversion; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.uom_conversion (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    from_uom_id bigint NOT NULL,
    to_uom_id bigint NOT NULL,
    factor numeric(18,6) NOT NULL,
    CONSTRAINT uom_conversion_factor_check CHECK ((factor > (0)::numeric))
);


--
-- Name: uom_conversion_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.uom_conversion ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.uom_conversion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: uom_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.uom ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.uom_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: uom_set; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.uom_set (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    is_active boolean DEFAULT true,
    created_at timestamp with time zone DEFAULT now(),
    category character varying(30),
    updated_at timestamp with time zone DEFAULT now()
);


--
-- Name: uom_set_dtl; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.uom_set_dtl (
    id bigint NOT NULL,
    uom_set_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    parent_uom_id bigint,
    conversion_factor numeric(19,6) DEFAULT 1 NOT NULL,
    level integer DEFAULT 1 NOT NULL,
    is_base boolean DEFAULT false
);


--
-- Name: uom_set_dtl_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.uom_set_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: uom_set_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.uom_set_dtl_id_seq OWNED BY md.uom_set_dtl.id;


--
-- Name: uom_set_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.uom_set_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: uom_set_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.uom_set_id_seq OWNED BY md.uom_set.id;


--
-- Name: vehicle; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.vehicle (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    plate_no character varying(20) NOT NULL,
    vehicle_type character varying(50),
    capacity_kg numeric(10,2),
    driver_id bigint,
    status character varying(16) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: vehicle_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.vehicle_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vehicle_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.vehicle_id_seq OWNED BY md.vehicle.id;


--
-- Name: warehouse; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.warehouse (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    address text,
    status character varying(16) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT warehouse_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: warehouse_bin; Type: TABLE; Schema: md; Owner: -
--

CREATE TABLE md.warehouse_bin (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    warehouse_id bigint NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(50),
    capacity numeric(19,4),
    is_active boolean DEFAULT true
);


--
-- Name: warehouse_bin_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

CREATE SEQUENCE md.warehouse_bin_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouse_bin_id_seq; Type: SEQUENCE OWNED BY; Schema: md; Owner: -
--

ALTER SEQUENCE md.warehouse_bin_id_seq OWNED BY md.warehouse_bin.id;


--
-- Name: warehouse_id_seq; Type: SEQUENCE; Schema: md; Owner: -
--

ALTER TABLE md.warehouse ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME md.warehouse_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: grn_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.grn_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty_received numeric(18,4) NOT NULL,
    po_detail_id bigint,
    qty_base numeric(19,4),
    CONSTRAINT grn_dtl_qty_received_check CHECK ((qty_received > (0)::numeric))
);


--
-- Name: grn_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.grn_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.grn_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grn_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.grn_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    po_id bigint NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    CONSTRAINT grn_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: grn_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.grn_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.grn_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: price_list; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.price_list (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    item_id bigint NOT NULL,
    price numeric(19,4) NOT NULL,
    currency character varying(3) DEFAULT 'IDR'::character varying,
    valid_from date NOT NULL,
    valid_to date,
    min_qty numeric(19,4) DEFAULT 1,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    uom_id bigint
);


--
-- Name: price_list_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

CREATE SEQUENCE pur.price_list_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: price_list_id_seq; Type: SEQUENCE OWNED BY; Schema: pur; Owner: -
--

ALTER SEQUENCE pur.price_list_id_seq OWNED BY pur.price_list.id;


--
-- Name: purchase_invoice_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_invoice_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) DEFAULT 0 NOT NULL,
    total numeric(18,4) DEFAULT 0 NOT NULL,
    CONSTRAINT purchase_invoice_dtl_qty_check CHECK ((qty > (0)::numeric))
);


--
-- Name: purchase_invoice_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_invoice_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_invoice_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_invoice_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_invoice_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    supplier_invoice_no character varying(50) NOT NULL,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    supplier_id bigint NOT NULL,
    grn_id bigint,
    total_amount numeric(18,4) DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    paid_amount numeric(19,4) DEFAULT 0,
    tax_type character varying(16) DEFAULT 'NO_TAX'::character varying,
    tax_amount numeric(18,4) DEFAULT 0,
    CONSTRAINT purchase_invoice_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'POSTED'::character varying, 'PAID'::character varying, 'PARTIAL'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: purchase_invoice_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_invoice_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_invoice_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_order_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_order_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) DEFAULT 0 NOT NULL,
    subtotal numeric(18,4) DEFAULT 0,
    dpp numeric(18,4) DEFAULT 0,
    tax_amount numeric(18,4) DEFAULT 0,
    line_total numeric(18,4) DEFAULT 0,
    pr_dtl_id bigint,
    qty_base numeric(19,4),
    CONSTRAINT purchase_order_dtl_qty_check CHECK ((qty > (0)::numeric))
);


--
-- Name: purchase_order_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_order_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_order_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_order_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_order_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint,
    doc_no character varying(50) NOT NULL,
    doc_date date,
    supplier_id bigint,
    pr_id bigint,
    remarks text,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    tax_type character varying(16) DEFAULT 'NO_TAX'::character varying,
    tax_amount numeric(18,4) DEFAULT 0,
    total_amount numeric(18,4) DEFAULT 0,
    rfq_id bigint,
    po_type character varying(20) DEFAULT 'FROM_PR'::character varying NOT NULL,
    bypass_reason text,
    bypass_approved_by bigint,
    bypass_approved_at timestamp with time zone,
    CONSTRAINT purchase_order_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'RECEIVED'::character varying, 'VOID'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: purchase_order_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_order_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_order_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_policy; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_policy (
    id bigint NOT NULL,
    company_id bigint NOT NULL,
    rule_name character varying(100) NOT NULL,
    rule_type character varying(30) NOT NULL,
    threshold_value numeric(18,4),
    reference_id bigint,
    requires_pr boolean NOT NULL,
    priority integer DEFAULT 100 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: purchase_policy_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_policy ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_request_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_request_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    short_text character varying(255),
    delivery_date date,
    estimated_price numeric(18,4) DEFAULT 0,
    account_assignment character varying(30),
    material_group character varying(30),
    converted_po_id bigint,
    qty_base numeric(19,4),
    CONSTRAINT purchase_request_dtl_qty_check CHECK ((qty > (0)::numeric))
);


--
-- Name: purchase_request_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_request_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_request_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_request_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_request_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    remarks text,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    doc_type character varying(20) DEFAULT 'STANDARD'::character varying,
    purchasing_org character varying(20) DEFAULT '1000'::character varying,
    purchasing_group character varying(20) DEFAULT '001'::character varying,
    requisitioner_id bigint,
    CONSTRAINT pr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'PO_CREATED'::character varying, 'VOID'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: purchase_request_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_request_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_request_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_return_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_return_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) NOT NULL,
    total numeric(18,4) NOT NULL,
    subtotal numeric(19,2)
);


--
-- Name: purchase_return_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_return_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_return_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: purchase_return_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.purchase_return_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    supplier_id bigint NOT NULL,
    purchase_invoice_id bigint,
    total_amount numeric(18,4) NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    warehouse_id bigint,
    tax_amount numeric(18,4) DEFAULT 0
);


--
-- Name: purchase_return_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

ALTER TABLE pur.purchase_return_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME pur.purchase_return_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: rfq_dtl; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.rfq_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    remarks text
);


--
-- Name: rfq_dtl_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

CREATE SEQUENCE pur.rfq_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rfq_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: pur; Owner: -
--

ALTER SEQUENCE pur.rfq_dtl_id_seq OWNED BY pur.rfq_dtl.id;


--
-- Name: rfq_hdr; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.rfq_hdr (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    due_date date,
    pr_id bigint,
    remarks text,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    created_at timestamp with time zone DEFAULT now(),
    recommended_supplier_id bigint,
    recommended_amount numeric(18,4) DEFAULT 0,
    current_level integer DEFAULT 0,
    total_level integer DEFAULT 0
);


--
-- Name: rfq_hdr_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

CREATE SEQUENCE pur.rfq_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rfq_hdr_id_seq; Type: SEQUENCE OWNED BY; Schema: pur; Owner: -
--

ALTER SEQUENCE pur.rfq_hdr_id_seq OWNED BY pur.rfq_hdr.id;


--
-- Name: rfq_response; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.rfq_response (
    id bigint NOT NULL,
    rfq_supplier_id bigint NOT NULL,
    rfq_dtl_id bigint NOT NULL,
    unit_price numeric(18,4) DEFAULT 0 NOT NULL,
    lead_time_days integer,
    remarks text
);


--
-- Name: rfq_response_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

CREATE SEQUENCE pur.rfq_response_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rfq_response_id_seq; Type: SEQUENCE OWNED BY; Schema: pur; Owner: -
--

ALTER SEQUENCE pur.rfq_response_id_seq OWNED BY pur.rfq_response.id;


--
-- Name: rfq_supplier; Type: TABLE; Schema: pur; Owner: -
--

CREATE TABLE pur.rfq_supplier (
    id bigint NOT NULL,
    rfq_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    sent_date date,
    status character varying(16) DEFAULT 'PENDING'::character varying,
    remarks text
);


--
-- Name: rfq_supplier_id_seq; Type: SEQUENCE; Schema: pur; Owner: -
--

CREATE SEQUENCE pur.rfq_supplier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rfq_supplier_id_seq; Type: SEQUENCE OWNED BY; Schema: pur; Owner: -
--

ALTER SEQUENCE pur.rfq_supplier_id_seq OWNED BY pur.rfq_supplier.id;


--
-- Name: delivery_order_dtl; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.delivery_order_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    cogs_unit numeric(18,4) DEFAULT 0,
    cogs_total numeric(18,4) DEFAULT 0,
    gi_posted_qty numeric(18,4) DEFAULT 0 NOT NULL,
    qty_base numeric(19,4),
    CONSTRAINT ck_sal_do_dtl_gi_posted_qty_do_gi CHECK (((gi_posted_qty >= (0)::numeric) AND (gi_posted_qty <= qty)))
);


--
-- Name: delivery_order_dtl_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.delivery_order_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.delivery_order_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: delivery_order_hdr; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.delivery_order_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint DEFAULT 1,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    so_id bigint NOT NULL,
    status character varying(24) DEFAULT 'DRAFT'::character varying NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    customer_id bigint,
    warehouse_id bigint,
    picking_status character varying(16) DEFAULT 'NOT_STARTED'::character varying NOT NULL,
    goods_issue_status character varying(20) DEFAULT 'NOT_POSTED'::character varying NOT NULL,
    pod_status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    delivered_at timestamp with time zone,
    delivered_by character varying(100),
    pod_reference character varying(100),
    pod_remarks text,
    CONSTRAINT ck_sal_do_goods_issue_status_do_gi CHECK (((goods_issue_status)::text = ANY ((ARRAY['NOT_POSTED'::character varying, 'PARTIAL'::character varying, 'POSTED'::character varying, 'REVERSED'::character varying, 'LEGACY_REVIEW'::character varying])::text[]))),
    CONSTRAINT ck_sal_do_picking_status_do_gi CHECK (((picking_status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'IN_PROGRESS'::character varying, 'READY'::character varying])::text[]))),
    CONSTRAINT ck_sal_do_pod_status_do_gi CHECK (((pod_status)::text = ANY ((ARRAY['PENDING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT delivery_order_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'IN_PICKING'::character varying, 'READY_TO_SHIP'::character varying, 'PARTIALLY_SHIPPED'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CLOSED'::character varying, 'CANCELLED'::character varying, 'CONFIRMED'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: delivery_order_hdr_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.delivery_order_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.delivery_order_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_invoice_dtl; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_invoice_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) NOT NULL,
    total numeric(18,4) NOT NULL,
    uom_id bigint,
    dpp numeric(19,2) DEFAULT 0,
    ppn_amount numeric(19,2) DEFAULT 0,
    tax_type character varying(20)
);


--
-- Name: sales_invoice_dtl_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_invoice_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_invoice_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_invoice_hdr; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_invoice_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint DEFAULT 1,
    doc_no character varying(50) NOT NULL,
    doc_date date DEFAULT CURRENT_DATE,
    due_date date NOT NULL,
    do_id bigint,
    customer_id bigint NOT NULL,
    total_amount numeric(18,4) DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT 'POSTED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    coretax_status character varying(30) DEFAULT 'PENDING'::character varying,
    nsfp character varying(20),
    coretax_message text,
    tax_amount numeric(18,4) DEFAULT 0,
    remarks text,
    invoice_date date,
    paid_amount numeric(19,4) DEFAULT 0,
    tax_type character varying(16) DEFAULT 'NO_TAX'::character varying,
    subtotal numeric(19,2) DEFAULT 0,
    dpp numeric(19,2) DEFAULT 0,
    ppn_rate numeric(5,2) DEFAULT 11,
    salesperson_id bigint,
    CONSTRAINT sales_invoice_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'POSTED'::character varying, 'PAID'::character varying, 'PARTIAL'::character varying, 'VOID'::character varying])::text[])))
);


--
-- Name: sales_invoice_hdr_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_invoice_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_invoice_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_order_dtl; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_order_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) DEFAULT 0 NOT NULL,
    tax_amount numeric(18,4) DEFAULT 0,
    line_total numeric(18,4) DEFAULT 0,
    subtotal numeric(18,4) DEFAULT 0,
    dpp numeric(18,4) DEFAULT 0,
    qty_base numeric(19,4),
    CONSTRAINT sales_order_dtl_qty_check CHECK ((qty > (0)::numeric))
);


--
-- Name: sales_order_dtl_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_order_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_order_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_order_hdr; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_order_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    customer_id bigint NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying NOT NULL,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    currency character(3) DEFAULT 'IDR'::bpchar,
    tax_type character varying(16) DEFAULT 'NO_TAX'::character varying,
    tax_amount numeric(18,4) DEFAULT 0,
    total_amount numeric(18,4) DEFAULT 0,
    salesperson_id bigint,
    CONSTRAINT sales_order_hdr_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'PARTIAL'::character varying, 'DELIVERED'::character varying, 'INVOICED'::character varying, 'VOID'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: sales_order_hdr_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_order_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_order_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_quotation_dtl; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_quotation_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) DEFAULT 0 NOT NULL,
    subtotal numeric(18,4) DEFAULT 0 NOT NULL,
    tax_amount numeric(18,4) DEFAULT 0 NOT NULL,
    total numeric(18,4) DEFAULT 0 NOT NULL,
    remarks text
);


--
-- Name: sales_quotation_dtl_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

CREATE SEQUENCE sal.sales_quotation_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_quotation_dtl_id_seq; Type: SEQUENCE OWNED BY; Schema: sal; Owner: -
--

ALTER SEQUENCE sal.sales_quotation_dtl_id_seq OWNED BY sal.sales_quotation_dtl.id;


--
-- Name: sales_quotation_hdr; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_quotation_hdr (
    id bigint NOT NULL,
    tenant_id bigint DEFAULT 1 NOT NULL,
    company_id bigint DEFAULT 1 NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    valid_until date,
    customer_id bigint NOT NULL,
    currency character varying(3) DEFAULT 'IDR'::character varying,
    remarks text,
    status character varying(20) DEFAULT 'DRAFT'::character varying,
    total_amount numeric(18,4) DEFAULT 0,
    tax_type character varying(20) DEFAULT 'EXCLUDE'::character varying,
    tax_amount numeric(18,4) DEFAULT 0,
    converted_so_id bigint,
    created_at timestamp with time zone DEFAULT now()
);


--
-- Name: sales_quotation_hdr_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

CREATE SEQUENCE sal.sales_quotation_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_quotation_hdr_id_seq; Type: SEQUENCE OWNED BY; Schema: sal; Owner: -
--

ALTER SEQUENCE sal.sales_quotation_hdr_id_seq OWNED BY sal.sales_quotation_hdr.id;


--
-- Name: sales_return_dtl; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_return_dtl (
    id bigint NOT NULL,
    header_id bigint NOT NULL,
    item_id bigint NOT NULL,
    uom_id bigint NOT NULL,
    qty numeric(18,4) NOT NULL,
    price numeric(18,4) NOT NULL,
    total numeric(18,4) NOT NULL
);


--
-- Name: sales_return_dtl_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_return_dtl ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_return_dtl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: sales_return_hdr; Type: TABLE; Schema: sal; Owner: -
--

CREATE TABLE sal.sales_return_hdr (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    doc_no character varying(50) NOT NULL,
    doc_date date NOT NULL,
    customer_id bigint NOT NULL,
    sales_invoice_id bigint,
    total_amount numeric(18,4) NOT NULL,
    status character varying(16) DEFAULT 'DRAFT'::character varying,
    remarks text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    warehouse_id bigint,
    tax_amount numeric(18,4) DEFAULT 0
);


--
-- Name: sales_return_hdr_id_seq; Type: SEQUENCE; Schema: sal; Owner: -
--

ALTER TABLE sal.sales_return_hdr ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sal.sales_return_hdr_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: app_config; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.app_config (
    tenant_id bigint NOT NULL,
    company_id bigint NOT NULL,
    config_key character varying(100) NOT NULL,
    config_value text,
    data_type character varying(16) DEFAULT 'STRING'::character varying NOT NULL,
    description text,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT app_config_data_type_check CHECK (((data_type)::text = ANY ((ARRAY['STRING'::character varying, 'INT'::character varying, 'DECIMAL'::character varying, 'BOOLEAN'::character varying, 'JSON'::character varying, 'DATE'::character varying])::text[])))
);


--
-- Name: company; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.company (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    address text,
    code character varying(20),
    phone character varying(50),
    email character varying(100)
);


--
-- Name: company_id_seq; Type: SEQUENCE; Schema: sys; Owner: -
--

CREATE SEQUENCE sys.company_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: company_id_seq; Type: SEQUENCE OWNED BY; Schema: sys; Owner: -
--

ALTER SEQUENCE sys.company_id_seq OWNED BY sys.company.id;


--
-- Name: coretax_log; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.coretax_log (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    request_payload text,
    response_payload text,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: coretax_log_id_seq; Type: SEQUENCE; Schema: sys; Owner: -
--

ALTER TABLE sys.coretax_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sys.coretax_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: doc_number_counter; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.doc_number_counter (
    company_id bigint NOT NULL,
    doc_type character varying(30) NOT NULL,
    period character varying(6) NOT NULL,
    prefix character varying(20) NOT NULL,
    last_number bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: job; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.job (
    id bigint NOT NULL,
    job_name character varying(100) NOT NULL,
    job_group character varying(50) NOT NULL,
    cron_expr character varying(100) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    last_run timestamp with time zone,
    last_status character varying(16),
    last_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: job_id_seq; Type: SEQUENCE; Schema: sys; Owner: -
--

ALTER TABLE sys.job ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sys.job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: outbox_event; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.outbox_event (
    id bigint NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_type character varying(50) NOT NULL,
    aggregate_id text NOT NULL,
    payload jsonb NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts smallint DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    processed_at timestamp with time zone,
    CONSTRAINT outbox_event_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: outbox_event_id_seq; Type: SEQUENCE; Schema: sys; Owner: -
--

ALTER TABLE sys.outbox_event ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME sys.outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_company; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.user_company (
    user_id bigint NOT NULL,
    company_id bigint NOT NULL
);


--
-- Name: user_permission; Type: TABLE; Schema: sys; Owner: -
--

CREATE TABLE sys.user_permission (
    user_id bigint NOT NULL,
    permission_code character varying(100) NOT NULL
);


--
-- Name: audit_log_2026_11; Type: TABLE ATTACH; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log ATTACH PARTITION core.audit_log_2026_11 FOR VALUES FROM ('2026-11-01 00:00:00+07') TO ('2026-12-01 00:00:00+07');


--
-- Name: audit_log_2026_12; Type: TABLE ATTACH; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log ATTACH PARTITION core.audit_log_2026_12 FOR VALUES FROM ('2026-12-01 00:00:00+07') TO ('2027-01-01 00:00:00+07');


--
-- Name: audit_log_2027_01; Type: TABLE ATTACH; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log ATTACH PARTITION core.audit_log_2027_01 FOR VALUES FROM ('2027-01-01 00:00:00+07') TO ('2027-02-01 00:00:00+07');


--
-- Name: approval_log id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.approval_log ALTER COLUMN id SET DEFAULT nextval('core.approval_log_id_seq'::regclass);


--
-- Name: approval_matrix id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.approval_matrix ALTER COLUMN id SET DEFAULT nextval('core.approval_matrix_id_seq'::regclass);


--
-- Name: number_range id; Type: DEFAULT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.number_range ALTER COLUMN id SET DEFAULT nextval('core.number_range_id_seq'::regclass);


--
-- Name: ap_payment_dtl id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ap_payment_dtl ALTER COLUMN id SET DEFAULT nextval('fin.ap_payment_dtl_id_seq'::regclass);


--
-- Name: ar_receipt_dtl id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ar_receipt_dtl ALTER COLUMN id SET DEFAULT nextval('fin.ar_receipt_dtl_id_seq'::regclass);


--
-- Name: cost_center id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cost_center ALTER COLUMN id SET DEFAULT nextval('fin.cost_center_id_seq'::regclass);


--
-- Name: fiscal_period id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fiscal_period ALTER COLUMN id SET DEFAULT nextval('fin.fiscal_period_id_seq'::regclass);


--
-- Name: fixed_asset id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fixed_asset ALTER COLUMN id SET DEFAULT nextval('fin.fixed_asset_id_seq'::regclass);


--
-- Name: payment_dtl id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_dtl ALTER COLUMN id SET DEFAULT nextval('fin.payment_dtl_id_seq'::regclass);


--
-- Name: payment_hdr id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_hdr ALTER COLUMN id SET DEFAULT nextval('fin.payment_hdr_id_seq'::regclass);


--
-- Name: payment_method id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_method ALTER COLUMN id SET DEFAULT nextval('fin.payment_method_id_seq'::regclass);


--
-- Name: profit_center id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.profit_center ALTER COLUMN id SET DEFAULT nextval('fin.profit_center_id_seq'::regclass);


--
-- Name: purchase_order id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_order ALTER COLUMN id SET DEFAULT nextval('fin.purchase_order_id_seq'::regclass);


--
-- Name: purchase_policy id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_policy ALTER COLUMN id SET DEFAULT nextval('fin.purchase_policy_id_seq'::regclass);


--
-- Name: purchase_requisition id; Type: DEFAULT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_requisition ALTER COLUMN id SET DEFAULT nextval('fin.purchase_requisition_id_seq'::regclass);


--
-- Name: department id; Type: DEFAULT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.department ALTER COLUMN id SET DEFAULT nextval('hr.department_id_seq'::regclass);


--
-- Name: position id; Type: DEFAULT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr."position" ALTER COLUMN id SET DEFAULT nextval('hr.position_id_seq'::regclass);


--
-- Name: stock_balance id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_balance ALTER COLUMN id SET DEFAULT nextval('inv.stock_balance_id_seq'::regclass);


--
-- Name: stock_movement id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_movement ALTER COLUMN id SET DEFAULT nextval('inv.stock_movement_id_seq'::regclass);


--
-- Name: stock_opname_dtl id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_opname_dtl ALTER COLUMN id SET DEFAULT nextval('inv.stock_opname_dtl_id_seq'::regclass);


--
-- Name: stock_opname_hdr id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_opname_hdr ALTER COLUMN id SET DEFAULT nextval('inv.stock_opname_hdr_id_seq'::regclass);


--
-- Name: stock_period_snapshot id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_period_snapshot ALTER COLUMN id SET DEFAULT nextval('inv.stock_period_snapshot_id_seq'::regclass);


--
-- Name: stock_transfer_dtl id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_transfer_dtl ALTER COLUMN id SET DEFAULT nextval('inv.stock_transfer_dtl_id_seq'::regclass);


--
-- Name: stock_transfer_hdr id; Type: DEFAULT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_transfer_hdr ALTER COLUMN id SET DEFAULT nextval('inv.stock_transfer_hdr_id_seq'::regclass);


--
-- Name: bom_detail id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_detail ALTER COLUMN id SET DEFAULT nextval('md.bom_detail_id_seq'::regclass);


--
-- Name: bom_header id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_header ALTER COLUMN id SET DEFAULT nextval('md.bom_header_id_seq'::regclass);


--
-- Name: company id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.company ALTER COLUMN id SET DEFAULT nextval('md.company_id_seq'::regclass);


--
-- Name: driver id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.driver ALTER COLUMN id SET DEFAULT nextval('md.driver_id_seq'::regclass);


--
-- Name: item_batch id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_batch ALTER COLUMN id SET DEFAULT nextval('md.item_batch_id_seq'::regclass);


--
-- Name: item_serial id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_serial ALTER COLUMN id SET DEFAULT nextval('md.item_serial_id_seq'::regclass);


--
-- Name: payment_term id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.payment_term ALTER COLUMN id SET DEFAULT nextval('md.payment_term_id_seq'::regclass);


--
-- Name: salesperson id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.salesperson ALTER COLUMN id SET DEFAULT nextval('md.salesperson_id_seq'::regclass);


--
-- Name: shipping_method id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.shipping_method ALTER COLUMN id SET DEFAULT nextval('md.shipping_method_id_seq'::regclass);


--
-- Name: uom_set id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set ALTER COLUMN id SET DEFAULT nextval('md.uom_set_id_seq'::regclass);


--
-- Name: uom_set_dtl id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl ALTER COLUMN id SET DEFAULT nextval('md.uom_set_dtl_id_seq'::regclass);


--
-- Name: vehicle id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.vehicle ALTER COLUMN id SET DEFAULT nextval('md.vehicle_id_seq'::regclass);


--
-- Name: warehouse_bin id; Type: DEFAULT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse_bin ALTER COLUMN id SET DEFAULT nextval('md.warehouse_bin_id_seq'::regclass);


--
-- Name: price_list id; Type: DEFAULT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.price_list ALTER COLUMN id SET DEFAULT nextval('pur.price_list_id_seq'::regclass);


--
-- Name: rfq_dtl id; Type: DEFAULT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_dtl ALTER COLUMN id SET DEFAULT nextval('pur.rfq_dtl_id_seq'::regclass);


--
-- Name: rfq_hdr id; Type: DEFAULT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_hdr ALTER COLUMN id SET DEFAULT nextval('pur.rfq_hdr_id_seq'::regclass);


--
-- Name: rfq_response id; Type: DEFAULT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_response ALTER COLUMN id SET DEFAULT nextval('pur.rfq_response_id_seq'::regclass);


--
-- Name: rfq_supplier id; Type: DEFAULT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_supplier ALTER COLUMN id SET DEFAULT nextval('pur.rfq_supplier_id_seq'::regclass);


--
-- Name: sales_quotation_dtl id; Type: DEFAULT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_dtl ALTER COLUMN id SET DEFAULT nextval('sal.sales_quotation_dtl_id_seq'::regclass);


--
-- Name: sales_quotation_hdr id; Type: DEFAULT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_hdr ALTER COLUMN id SET DEFAULT nextval('sal.sales_quotation_hdr_id_seq'::regclass);


--
-- Name: company id; Type: DEFAULT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.company ALTER COLUMN id SET DEFAULT nextval('sys.company_id_seq'::regclass);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: app_user app_user_tenant_id_username_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.app_user
    ADD CONSTRAINT app_user_tenant_id_username_key UNIQUE (tenant_id, username);


--
-- Name: approval_log approval_log_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.approval_log
    ADD CONSTRAINT approval_log_pkey PRIMARY KEY (id);


--
-- Name: approval_matrix approval_matrix_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.approval_matrix
    ADD CONSTRAINT approval_matrix_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id, created_at);


--
-- Name: audit_log_2026_11 audit_log_2026_11_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log_2026_11
    ADD CONSTRAINT audit_log_2026_11_pkey PRIMARY KEY (id, created_at);


--
-- Name: audit_log_2026_12 audit_log_2026_12_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log_2026_12
    ADD CONSTRAINT audit_log_2026_12_pkey PRIMARY KEY (id, created_at);


--
-- Name: audit_log_2027_01 audit_log_2027_01_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.audit_log_2027_01
    ADD CONSTRAINT audit_log_2027_01_pkey PRIMARY KEY (id, created_at);


--
-- Name: company company_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.company
    ADD CONSTRAINT company_pkey PRIMARY KEY (id);


--
-- Name: company company_tenant_id_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.company
    ADD CONSTRAINT company_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: menu menu_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.menu
    ADD CONSTRAINT menu_code_key UNIQUE (code);


--
-- Name: menu menu_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.menu
    ADD CONSTRAINT menu_pkey PRIMARY KEY (id);


--
-- Name: number_range number_range_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.number_range
    ADD CONSTRAINT number_range_pkey PRIMARY KEY (id);


--
-- Name: number_range number_range_tenant_id_doc_type_period_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.number_range
    ADD CONSTRAINT number_range_tenant_id_doc_type_period_key UNIQUE (tenant_id, doc_type, period);


--
-- Name: permission permission_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.permission
    ADD CONSTRAINT permission_code_key UNIQUE (code);


--
-- Name: permission permission_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.permission
    ADD CONSTRAINT permission_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);


--
-- Name: refresh_token refresh_token_token_hash_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.refresh_token
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash);


--
-- Name: role_permission role_permission_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role_permission
    ADD CONSTRAINT role_permission_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role
    ADD CONSTRAINT role_pkey PRIMARY KEY (id);


--
-- Name: role role_tenant_id_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role
    ADD CONSTRAINT role_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: tenant tenant_code_key; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.tenant
    ADD CONSTRAINT tenant_code_key UNIQUE (code);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: approval_matrix uq_approval_matrix; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.approval_matrix
    ADD CONSTRAINT uq_approval_matrix UNIQUE (tenant_id, doc_type, level);


--
-- Name: user_company user_company_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_company
    ADD CONSTRAINT user_company_pkey PRIMARY KEY (user_id, company_id);


--
-- Name: user_role user_role_pkey; Type: CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_role
    ADD CONSTRAINT user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: ap_payment_dtl ap_payment_dtl_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ap_payment_dtl
    ADD CONSTRAINT ap_payment_dtl_pkey PRIMARY KEY (id);


--
-- Name: ap_payment ap_payment_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ap_payment
    ADD CONSTRAINT ap_payment_pkey PRIMARY KEY (id);


--
-- Name: ar_collection ar_collection_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ar_collection
    ADD CONSTRAINT ar_collection_pkey PRIMARY KEY (id);


--
-- Name: ar_receipt_dtl ar_receipt_dtl_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ar_receipt_dtl
    ADD CONSTRAINT ar_receipt_dtl_pkey PRIMARY KEY (id);


--
-- Name: asset_depreciation_log asset_depreciation_log_asset_id_period_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.asset_depreciation_log
    ADD CONSTRAINT asset_depreciation_log_asset_id_period_key UNIQUE (asset_id, period);


--
-- Name: asset_depreciation_log asset_depreciation_log_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.asset_depreciation_log
    ADD CONSTRAINT asset_depreciation_log_pkey PRIMARY KEY (id);


--
-- Name: asset_master asset_master_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.asset_master
    ADD CONSTRAINT asset_master_pkey PRIMARY KEY (id);


--
-- Name: bank_account bank_account_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.bank_account
    ADD CONSTRAINT bank_account_pkey PRIMARY KEY (id);


--
-- Name: cash_bank_trx cash_bank_trx_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cash_bank_trx
    ADD CONSTRAINT cash_bank_trx_pkey PRIMARY KEY (id);


--
-- Name: chart_of_account chart_of_account_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.chart_of_account
    ADD CONSTRAINT chart_of_account_pkey PRIMARY KEY (id);


--
-- Name: chart_of_account chart_of_account_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.chart_of_account
    ADD CONSTRAINT chart_of_account_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: cost_center cost_center_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cost_center
    ADD CONSTRAINT cost_center_pkey PRIMARY KEY (id);


--
-- Name: cost_center cost_center_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cost_center
    ADD CONSTRAINT cost_center_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: fiscal_period_audit fiscal_period_audit_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fiscal_period_audit
    ADD CONSTRAINT fiscal_period_audit_pkey PRIMARY KEY (id);


--
-- Name: fiscal_period fiscal_period_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fiscal_period
    ADD CONSTRAINT fiscal_period_pkey PRIMARY KEY (id);


--
-- Name: fiscal_period fiscal_period_tenant_id_company_id_year_month_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fiscal_period
    ADD CONSTRAINT fiscal_period_tenant_id_company_id_year_month_key UNIQUE (tenant_id, company_id, year, month);


--
-- Name: fixed_asset fixed_asset_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fixed_asset
    ADD CONSTRAINT fixed_asset_pkey PRIMARY KEY (id);


--
-- Name: investment investment_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.investment
    ADD CONSTRAINT investment_pkey PRIMARY KEY (id);


--
-- Name: journal_dtl journal_dtl_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.journal_dtl
    ADD CONSTRAINT journal_dtl_pkey PRIMARY KEY (id);


--
-- Name: journal_hdr journal_hdr_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.journal_hdr
    ADD CONSTRAINT journal_hdr_pkey PRIMARY KEY (id);


--
-- Name: notes notes_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.notes
    ADD CONSTRAINT notes_pkey PRIMARY KEY (id);


--
-- Name: payment_dtl payment_dtl_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_dtl
    ADD CONSTRAINT payment_dtl_pkey PRIMARY KEY (id);


--
-- Name: payment_hdr payment_hdr_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_hdr
    ADD CONSTRAINT payment_hdr_pkey PRIMARY KEY (id);


--
-- Name: payment_method payment_method_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_method
    ADD CONSTRAINT payment_method_pkey PRIMARY KEY (id);


--
-- Name: payment_method payment_method_tenant_id_code_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_method
    ADD CONSTRAINT payment_method_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: profit_center profit_center_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.profit_center
    ADD CONSTRAINT profit_center_pkey PRIMARY KEY (id);


--
-- Name: profit_center profit_center_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.profit_center
    ADD CONSTRAINT profit_center_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: purchase_order purchase_order_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_order
    ADD CONSTRAINT purchase_order_pkey PRIMARY KEY (id);


--
-- Name: purchase_order purchase_order_tenant_id_po_number_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_order
    ADD CONSTRAINT purchase_order_tenant_id_po_number_key UNIQUE (tenant_id, po_number);


--
-- Name: purchase_policy purchase_policy_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_policy
    ADD CONSTRAINT purchase_policy_pkey PRIMARY KEY (id);


--
-- Name: purchase_requisition purchase_requisition_pkey; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_requisition
    ADD CONSTRAINT purchase_requisition_pkey PRIMARY KEY (id);


--
-- Name: purchase_requisition purchase_requisition_tenant_id_pr_number_key; Type: CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_requisition
    ADD CONSTRAINT purchase_requisition_tenant_id_pr_number_key UNIQUE (tenant_id, pr_number);


--
-- Name: attendance attendance_employee_id_att_date_key; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.attendance
    ADD CONSTRAINT attendance_employee_id_att_date_key UNIQUE (employee_id, att_date);


--
-- Name: attendance attendance_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.attendance
    ADD CONSTRAINT attendance_pkey PRIMARY KEY (id);


--
-- Name: cash_advance_expense cash_advance_expense_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.cash_advance_expense
    ADD CONSTRAINT cash_advance_expense_pkey PRIMARY KEY (id);


--
-- Name: cash_advance cash_advance_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.cash_advance
    ADD CONSTRAINT cash_advance_pkey PRIMARY KEY (id);


--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: department department_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.department
    ADD CONSTRAINT department_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: employee employee_nip_key; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.employee
    ADD CONSTRAINT employee_nip_key UNIQUE (nip);


--
-- Name: employee employee_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.employee
    ADD CONSTRAINT employee_pkey PRIMARY KEY (id);


--
-- Name: employee_salary employee_salary_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.employee_salary
    ADD CONSTRAINT employee_salary_pkey PRIMARY KEY (employee_id);


--
-- Name: payroll_dtl payroll_dtl_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.payroll_dtl
    ADD CONSTRAINT payroll_dtl_pkey PRIMARY KEY (id);


--
-- Name: payroll_run payroll_run_period_key; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.payroll_run
    ADD CONSTRAINT payroll_run_period_key UNIQUE (period);


--
-- Name: payroll_run payroll_run_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.payroll_run
    ADD CONSTRAINT payroll_run_pkey PRIMARY KEY (id);


--
-- Name: position position_pkey; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr."position"
    ADD CONSTRAINT position_pkey PRIMARY KEY (id);


--
-- Name: position position_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr."position"
    ADD CONSTRAINT position_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: inventory_period_control inventory_period_control_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.inventory_period_control
    ADD CONSTRAINT inventory_period_control_pkey PRIMARY KEY (id);


--
-- Name: stock_balance stock_balance_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_balance
    ADD CONSTRAINT stock_balance_pkey PRIMARY KEY (id);


--
-- Name: stock_movement stock_movement_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_movement
    ADD CONSTRAINT stock_movement_pkey PRIMARY KEY (id);


--
-- Name: stock_opname_dtl stock_opname_dtl_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_opname_dtl
    ADD CONSTRAINT stock_opname_dtl_pkey PRIMARY KEY (id);


--
-- Name: stock_opname_hdr stock_opname_hdr_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_opname_hdr
    ADD CONSTRAINT stock_opname_hdr_pkey PRIMARY KEY (id);


--
-- Name: stock_period_snapshot stock_period_snapshot_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_period_snapshot
    ADD CONSTRAINT stock_period_snapshot_pkey PRIMARY KEY (id);


--
-- Name: stock_transfer_dtl stock_transfer_dtl_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_transfer_dtl
    ADD CONSTRAINT stock_transfer_dtl_pkey PRIMARY KEY (id);


--
-- Name: stock_transfer_hdr stock_transfer_hdr_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_transfer_hdr
    ADD CONSTRAINT stock_transfer_hdr_pkey PRIMARY KEY (id);


--
-- Name: transaction_detail transaction_detail_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT transaction_detail_pkey PRIMARY KEY (id);


--
-- Name: transaction_header transaction_header_company_id_doc_no_key; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT transaction_header_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: transaction_header transaction_header_pkey; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT transaction_header_pkey PRIMARY KEY (id);


--
-- Name: inventory_period_control uq_inv_inventory_period_do_gi; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.inventory_period_control
    ADD CONSTRAINT uq_inv_inventory_period_do_gi UNIQUE (company_id, period);


--
-- Name: stock_balance uq_stock_balance; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_balance
    ADD CONSTRAINT uq_stock_balance UNIQUE (tenant_id, item_id, warehouse_id);


--
-- Name: stock_period_snapshot uq_stock_period_snapshot; Type: CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_period_snapshot
    ADD CONSTRAINT uq_stock_period_snapshot UNIQUE (tenant_id, warehouse_id, item_id, period);


--
-- Name: bom_detail bom_detail_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_detail
    ADD CONSTRAINT bom_detail_pkey PRIMARY KEY (id);


--
-- Name: bom_header bom_header_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_header
    ADD CONSTRAINT bom_header_pkey PRIMARY KEY (id);


--
-- Name: bom_header bom_header_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_header
    ADD CONSTRAINT bom_header_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: company company_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.company
    ADD CONSTRAINT company_pkey PRIMARY KEY (id);


--
-- Name: currency currency_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.currency
    ADD CONSTRAINT currency_pkey PRIMARY KEY (code);


--
-- Name: customer customer_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.customer
    ADD CONSTRAINT customer_pkey PRIMARY KEY (id);


--
-- Name: customer customer_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.customer
    ADD CONSTRAINT customer_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: djp_goods_code djp_goods_code_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.djp_goods_code
    ADD CONSTRAINT djp_goods_code_pkey PRIMARY KEY (code);


--
-- Name: driver driver_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.driver
    ADD CONSTRAINT driver_pkey PRIMARY KEY (id);


--
-- Name: exchange_rate exchange_rate_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.exchange_rate
    ADD CONSTRAINT exchange_rate_pkey PRIMARY KEY (id);


--
-- Name: exchange_rate exchange_rate_tenant_id_from_currency_to_currency_effective_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.exchange_rate
    ADD CONSTRAINT exchange_rate_tenant_id_from_currency_to_currency_effective_key UNIQUE (tenant_id, from_currency, to_currency, effective_date);


--
-- Name: item_batch item_batch_item_id_warehouse_id_batch_no_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_batch
    ADD CONSTRAINT item_batch_item_id_warehouse_id_batch_no_key UNIQUE (item_id, warehouse_id, batch_no);


--
-- Name: item_batch item_batch_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_batch
    ADD CONSTRAINT item_batch_pkey PRIMARY KEY (id);


--
-- Name: item_category item_category_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_category
    ADD CONSTRAINT item_category_pkey PRIMARY KEY (id);


--
-- Name: item_category item_category_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_category
    ADD CONSTRAINT item_category_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: item item_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_pkey PRIMARY KEY (id);


--
-- Name: item_price item_price_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT item_price_pkey PRIMARY KEY (id);


--
-- Name: item_serial item_serial_item_id_serial_no_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_serial
    ADD CONSTRAINT item_serial_item_id_serial_no_key UNIQUE (item_id, serial_no);


--
-- Name: item_serial item_serial_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_serial
    ADD CONSTRAINT item_serial_pkey PRIMARY KEY (id);


--
-- Name: item item_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: payment_term payment_term_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.payment_term
    ADD CONSTRAINT payment_term_pkey PRIMARY KEY (id);


--
-- Name: payment_term payment_term_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.payment_term
    ADD CONSTRAINT payment_term_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: salesperson salesperson_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.salesperson
    ADD CONSTRAINT salesperson_pkey PRIMARY KEY (id);


--
-- Name: salesperson salesperson_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.salesperson
    ADD CONSTRAINT salesperson_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: shipping_method shipping_method_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.shipping_method
    ADD CONSTRAINT shipping_method_pkey PRIMARY KEY (id);


--
-- Name: shipping_method shipping_method_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.shipping_method
    ADD CONSTRAINT shipping_method_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: supplier supplier_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.supplier
    ADD CONSTRAINT supplier_pkey PRIMARY KEY (id);


--
-- Name: supplier supplier_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.supplier
    ADD CONSTRAINT supplier_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: tax tax_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.tax
    ADD CONSTRAINT tax_pkey PRIMARY KEY (id);


--
-- Name: tax tax_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.tax
    ADD CONSTRAINT tax_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: uom_conversion uom_conversion_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_conversion
    ADD CONSTRAINT uom_conversion_pkey PRIMARY KEY (id);


--
-- Name: uom_conversion uom_conversion_tenant_id_from_uom_id_to_uom_id_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_conversion
    ADD CONSTRAINT uom_conversion_tenant_id_from_uom_id_to_uom_id_key UNIQUE (tenant_id, from_uom_id, to_uom_id);


--
-- Name: uom uom_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom
    ADD CONSTRAINT uom_pkey PRIMARY KEY (id);


--
-- Name: uom_set_dtl uom_set_dtl_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl
    ADD CONSTRAINT uom_set_dtl_pkey PRIMARY KEY (id);


--
-- Name: uom_set_dtl uom_set_dtl_uom_set_id_uom_id_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl
    ADD CONSTRAINT uom_set_dtl_uom_set_id_uom_id_key UNIQUE (uom_set_id, uom_id);


--
-- Name: uom_set uom_set_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set
    ADD CONSTRAINT uom_set_pkey PRIMARY KEY (id);


--
-- Name: uom_set uom_set_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set
    ADD CONSTRAINT uom_set_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: uom uom_tenant_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom
    ADD CONSTRAINT uom_tenant_id_code_key UNIQUE (tenant_id, code);


--
-- Name: item_price uq_item_price; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT uq_item_price UNIQUE (tenant_id, company_id, item_id, currency, valid_from);


--
-- Name: vehicle uq_vehicle_plate; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.vehicle
    ADD CONSTRAINT uq_vehicle_plate UNIQUE (tenant_id, plate_no);


--
-- Name: vehicle vehicle_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.vehicle
    ADD CONSTRAINT vehicle_pkey PRIMARY KEY (id);


--
-- Name: warehouse_bin warehouse_bin_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse_bin
    ADD CONSTRAINT warehouse_bin_pkey PRIMARY KEY (id);


--
-- Name: warehouse_bin warehouse_bin_warehouse_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse_bin
    ADD CONSTRAINT warehouse_bin_warehouse_id_code_key UNIQUE (warehouse_id, code);


--
-- Name: warehouse warehouse_pkey; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse
    ADD CONSTRAINT warehouse_pkey PRIMARY KEY (id);


--
-- Name: warehouse warehouse_tenant_id_company_id_code_key; Type: CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse
    ADD CONSTRAINT warehouse_tenant_id_company_id_code_key UNIQUE (tenant_id, company_id, code);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: grn_dtl grn_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_dtl
    ADD CONSTRAINT grn_dtl_pkey PRIMARY KEY (id);


--
-- Name: grn_hdr grn_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_hdr
    ADD CONSTRAINT grn_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: grn_hdr grn_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_hdr
    ADD CONSTRAINT grn_hdr_pkey PRIMARY KEY (id);


--
-- Name: price_list price_list_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.price_list
    ADD CONSTRAINT price_list_pkey PRIMARY KEY (id);


--
-- Name: purchase_invoice_dtl purchase_invoice_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_dtl
    ADD CONSTRAINT purchase_invoice_dtl_pkey PRIMARY KEY (id);


--
-- Name: purchase_invoice_hdr purchase_invoice_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_hdr
    ADD CONSTRAINT purchase_invoice_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: purchase_invoice_hdr purchase_invoice_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_hdr
    ADD CONSTRAINT purchase_invoice_hdr_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_dtl purchase_order_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_dtl
    ADD CONSTRAINT purchase_order_dtl_pkey PRIMARY KEY (id);


--
-- Name: purchase_order_hdr purchase_order_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: purchase_order_hdr purchase_order_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_pkey PRIMARY KEY (id);


--
-- Name: purchase_policy purchase_policy_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_policy
    ADD CONSTRAINT purchase_policy_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_dtl purchase_request_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_dtl
    ADD CONSTRAINT purchase_request_dtl_pkey PRIMARY KEY (id);


--
-- Name: purchase_request_hdr purchase_request_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_hdr
    ADD CONSTRAINT purchase_request_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: purchase_request_hdr purchase_request_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_hdr
    ADD CONSTRAINT purchase_request_hdr_pkey PRIMARY KEY (id);


--
-- Name: purchase_return_dtl purchase_return_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_dtl
    ADD CONSTRAINT purchase_return_dtl_pkey PRIMARY KEY (id);


--
-- Name: purchase_return_hdr purchase_return_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_hdr
    ADD CONSTRAINT purchase_return_hdr_pkey PRIMARY KEY (id);


--
-- Name: rfq_dtl rfq_dtl_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_dtl
    ADD CONSTRAINT rfq_dtl_pkey PRIMARY KEY (id);


--
-- Name: rfq_hdr rfq_hdr_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_hdr
    ADD CONSTRAINT rfq_hdr_pkey PRIMARY KEY (id);


--
-- Name: rfq_response rfq_response_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_response
    ADD CONSTRAINT rfq_response_pkey PRIMARY KEY (id);


--
-- Name: rfq_supplier rfq_supplier_pkey; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_supplier
    ADD CONSTRAINT rfq_supplier_pkey PRIMARY KEY (id);


--
-- Name: rfq_hdr uq_rfq_doc; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_hdr
    ADD CONSTRAINT uq_rfq_doc UNIQUE (tenant_id, doc_no);


--
-- Name: rfq_response uq_rfq_resp; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_response
    ADD CONSTRAINT uq_rfq_resp UNIQUE (rfq_supplier_id, rfq_dtl_id);


--
-- Name: rfq_supplier uq_rfq_supplier; Type: CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_supplier
    ADD CONSTRAINT uq_rfq_supplier UNIQUE (rfq_id, supplier_id);


--
-- Name: delivery_order_dtl delivery_order_dtl_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_dtl
    ADD CONSTRAINT delivery_order_dtl_pkey PRIMARY KEY (id);


--
-- Name: delivery_order_hdr delivery_order_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_hdr
    ADD CONSTRAINT delivery_order_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: delivery_order_hdr delivery_order_hdr_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_hdr
    ADD CONSTRAINT delivery_order_hdr_pkey PRIMARY KEY (id);


--
-- Name: sales_invoice_dtl sales_invoice_dtl_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_dtl
    ADD CONSTRAINT sales_invoice_dtl_pkey PRIMARY KEY (id);


--
-- Name: sales_invoice_hdr sales_invoice_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_hdr
    ADD CONSTRAINT sales_invoice_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: sales_invoice_hdr sales_invoice_hdr_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_hdr
    ADD CONSTRAINT sales_invoice_hdr_pkey PRIMARY KEY (id);


--
-- Name: sales_order_dtl sales_order_dtl_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_dtl
    ADD CONSTRAINT sales_order_dtl_pkey PRIMARY KEY (id);


--
-- Name: sales_order_hdr sales_order_hdr_company_id_doc_no_key; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_hdr
    ADD CONSTRAINT sales_order_hdr_company_id_doc_no_key UNIQUE (company_id, doc_no);


--
-- Name: sales_order_hdr sales_order_hdr_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_hdr
    ADD CONSTRAINT sales_order_hdr_pkey PRIMARY KEY (id);


--
-- Name: sales_quotation_dtl sales_quotation_dtl_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_dtl
    ADD CONSTRAINT sales_quotation_dtl_pkey PRIMARY KEY (id);


--
-- Name: sales_quotation_hdr sales_quotation_hdr_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_hdr
    ADD CONSTRAINT sales_quotation_hdr_pkey PRIMARY KEY (id);


--
-- Name: sales_return_dtl sales_return_dtl_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_dtl
    ADD CONSTRAINT sales_return_dtl_pkey PRIMARY KEY (id);


--
-- Name: sales_return_hdr sales_return_hdr_pkey; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_hdr
    ADD CONSTRAINT sales_return_hdr_pkey PRIMARY KEY (id);


--
-- Name: sales_quotation_hdr uq_quotation_doc; Type: CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_hdr
    ADD CONSTRAINT uq_quotation_doc UNIQUE (tenant_id, doc_no);


--
-- Name: app_config app_config_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.app_config
    ADD CONSTRAINT app_config_pkey PRIMARY KEY (tenant_id, company_id, config_key);


--
-- Name: company company_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.company
    ADD CONSTRAINT company_pkey PRIMARY KEY (id);


--
-- Name: coretax_log coretax_log_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.coretax_log
    ADD CONSTRAINT coretax_log_pkey PRIMARY KEY (id);


--
-- Name: doc_number_counter doc_number_counter_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.doc_number_counter
    ADD CONSTRAINT doc_number_counter_pkey PRIMARY KEY (company_id, doc_type, period);


--
-- Name: job job_job_name_key; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.job
    ADD CONSTRAINT job_job_name_key UNIQUE (job_name);


--
-- Name: job job_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.job
    ADD CONSTRAINT job_pkey PRIMARY KEY (id);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);


--
-- Name: user_company user_company_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.user_company
    ADD CONSTRAINT user_company_pkey PRIMARY KEY (user_id, company_id);


--
-- Name: user_permission user_permission_pkey; Type: CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.user_permission
    ADD CONSTRAINT user_permission_pkey PRIMARY KEY (user_id, permission_code);


--
-- Name: idx_audit_record; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_audit_record ON ONLY core.audit_log USING btree (schema_name, table_name, record_id);


--
-- Name: audit_log_2026_11_schema_name_table_name_record_id_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2026_11_schema_name_table_name_record_id_idx ON core.audit_log_2026_11 USING btree (schema_name, table_name, record_id);


--
-- Name: idx_audit_user; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_audit_user ON ONLY core.audit_log USING btree (user_id, created_at DESC);


--
-- Name: audit_log_2026_11_user_id_created_at_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2026_11_user_id_created_at_idx ON core.audit_log_2026_11 USING btree (user_id, created_at DESC);


--
-- Name: audit_log_2026_12_schema_name_table_name_record_id_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2026_12_schema_name_table_name_record_id_idx ON core.audit_log_2026_12 USING btree (schema_name, table_name, record_id);


--
-- Name: audit_log_2026_12_user_id_created_at_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2026_12_user_id_created_at_idx ON core.audit_log_2026_12 USING btree (user_id, created_at DESC);


--
-- Name: audit_log_2027_01_schema_name_table_name_record_id_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2027_01_schema_name_table_name_record_id_idx ON core.audit_log_2027_01 USING btree (schema_name, table_name, record_id);


--
-- Name: audit_log_2027_01_user_id_created_at_idx; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX audit_log_2027_01_user_id_created_at_idx ON core.audit_log_2027_01 USING btree (user_id, created_at DESC);


--
-- Name: idx_approval_log_doc; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_approval_log_doc ON core.approval_log USING btree (doc_type, doc_id);


--
-- Name: idx_company_tenant; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_company_tenant ON core.company USING btree (tenant_id);


--
-- Name: idx_menu_parent; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_menu_parent ON core.menu USING btree (parent_id, sort_order);


--
-- Name: idx_menu_status; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_menu_status ON core.menu USING btree (status) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: idx_refresh_active; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_refresh_active ON core.refresh_token USING btree (expires_at) WHERE (revoked_at IS NULL);


--
-- Name: idx_refresh_family; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_refresh_family ON core.refresh_token USING btree (family_id);


--
-- Name: idx_refresh_user; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_refresh_user ON core.refresh_token USING btree (user_id);


--
-- Name: idx_user_email; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_user_email ON core.app_user USING btree (lower((email)::text));


--
-- Name: idx_user_status; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_user_status ON core.app_user USING btree (status) WHERE ((status)::text <> 'ACTIVE'::text);


--
-- Name: idx_user_tenant; Type: INDEX; Schema: core; Owner: -
--

CREATE INDEX idx_user_tenant ON core.app_user USING btree (tenant_id);


--
-- Name: idx_ap_pay_dtl_invoice; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_ap_pay_dtl_invoice ON fin.ap_payment_dtl USING btree (invoice_id);


--
-- Name: idx_ap_pay_dtl_payment; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_ap_pay_dtl_payment ON fin.ap_payment_dtl USING btree (payment_id);


--
-- Name: idx_ar_receipt_dtl_invoice; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_ar_receipt_dtl_invoice ON fin.ar_receipt_dtl USING btree (invoice_id);


--
-- Name: idx_ar_receipt_dtl_receipt; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_ar_receipt_dtl_receipt ON fin.ar_receipt_dtl USING btree (receipt_id);


--
-- Name: idx_coa_parent; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_coa_parent ON fin.chart_of_account USING btree (parent_id);


--
-- Name: idx_coa_type; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_coa_type ON fin.chart_of_account USING btree (account_type);


--
-- Name: idx_journal_acc; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_journal_acc ON fin.journal_dtl USING btree (account_id);


--
-- Name: idx_journal_date; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_journal_date ON fin.journal_hdr USING btree (doc_date);


--
-- Name: idx_journal_ref; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_journal_ref ON fin.journal_hdr USING btree (ref_type, ref_id);


--
-- Name: idx_po_pr_id; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX idx_po_pr_id ON fin.purchase_order USING btree (pr_id) WHERE (pr_id IS NOT NULL);


--
-- Name: ix_fin_fiscal_period_status; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX ix_fin_fiscal_period_status ON fin.fiscal_period USING btree (company_id, status, period);


--
-- Name: ix_fin_fp_audit_period; Type: INDEX; Schema: fin; Owner: -
--

CREATE INDEX ix_fin_fp_audit_period ON fin.fiscal_period_audit USING btree (company_id, period, performed_at DESC);


--
-- Name: uq_fin_fiscal_period_tenant_company_period; Type: INDEX; Schema: fin; Owner: -
--

CREATE UNIQUE INDEX uq_fin_fiscal_period_tenant_company_period ON fin.fiscal_period USING btree (tenant_id, company_id, period);


--
-- Name: idx_inv_inventory_period_status_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_inv_inventory_period_status_do_gi ON inv.inventory_period_control USING btree (company_id, status, period);


--
-- Name: idx_inv_trx_detail_do_line_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_inv_trx_detail_do_line_do_gi ON inv.transaction_detail USING btree (source_do_detail_id);


--
-- Name: idx_inv_trx_dtl; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_inv_trx_dtl ON inv.transaction_detail USING btree (header_id);


--
-- Name: idx_inv_trx_hdr; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_inv_trx_hdr ON inv.transaction_header USING btree (doc_date, doc_type);


--
-- Name: idx_inv_trx_source_do_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_inv_trx_source_do_do_gi ON inv.transaction_header USING btree (source_do_id, doc_type, id);


--
-- Name: idx_movement_item; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_movement_item ON inv.stock_movement USING btree (item_id, movement_date);


--
-- Name: idx_movement_ref; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_movement_ref ON inv.stock_movement USING btree (reference_type, reference_id);


--
-- Name: idx_snapshot_item; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_snapshot_item ON inv.stock_period_snapshot USING btree (item_id);


--
-- Name: idx_snapshot_period; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_snapshot_period ON inv.stock_period_snapshot USING btree (period);


--
-- Name: idx_stock_balance_item; Type: INDEX; Schema: inv; Owner: -
--

CREATE INDEX idx_stock_balance_item ON inv.stock_balance USING btree (item_id);


--
-- Name: uq_inv_gi_idempotency_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE UNIQUE INDEX uq_inv_gi_idempotency_do_gi ON inv.transaction_header USING btree (company_id, source_do_id, idempotency_key) WHERE ((source_do_id IS NOT NULL) AND (idempotency_key IS NOT NULL));


--
-- Name: uq_inv_gi_reversal_detail_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE UNIQUE INDEX uq_inv_gi_reversal_detail_do_gi ON inv.transaction_detail USING btree (reversal_of_detail_id) WHERE (reversal_of_detail_id IS NOT NULL);


--
-- Name: uq_inv_gi_reversal_header_do_gi; Type: INDEX; Schema: inv; Owner: -
--

CREATE UNIQUE INDEX uq_inv_gi_reversal_header_do_gi ON inv.transaction_header USING btree (reversal_of_header_id) WHERE (reversal_of_header_id IS NOT NULL);


--
-- Name: idx_driver_status; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_driver_status ON md.driver USING btree (status);


--
-- Name: idx_item_barcode; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_barcode ON md.item USING btree (barcode) WHERE (barcode IS NOT NULL);


--
-- Name: idx_item_category; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_category ON md.item USING btree (category_id);


--
-- Name: idx_item_name_trgm; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_name_trgm ON md.item USING gin (name public.gin_trgm_ops);


--
-- Name: idx_item_price_active; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_price_active ON md.item_price USING btree (item_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: idx_item_price_company; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_price_company ON md.item_price USING btree (company_id);


--
-- Name: idx_item_price_item; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_item_price_item ON md.item_price USING btree (item_id, valid_from);


--
-- Name: idx_uom_set_dtl_set; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_uom_set_dtl_set ON md.uom_set_dtl USING btree (uom_set_id);


--
-- Name: idx_vehicle_status; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_vehicle_status ON md.vehicle USING btree (status);


--
-- Name: idx_xrate_lookup; Type: INDEX; Schema: md; Owner: -
--

CREATE INDEX idx_xrate_lookup ON md.exchange_rate USING btree (from_currency, to_currency, effective_date DESC);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_po_company_type; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_po_company_type ON pur.purchase_order_hdr USING btree (company_id, po_type, doc_date DESC);


--
-- Name: idx_po_pr_id; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_po_pr_id ON pur.purchase_order_hdr USING btree (pr_id) WHERE (pr_id IS NOT NULL);


--
-- Name: idx_price_lookup; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_price_lookup ON pur.price_list USING btree (supplier_id, item_id, valid_from, valid_to);


--
-- Name: idx_pricelist_lookup; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_pricelist_lookup ON pur.price_list USING btree (supplier_id, item_id, valid_from, valid_to);


--
-- Name: idx_purchase_policy_company; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_purchase_policy_company ON pur.purchase_policy USING btree (company_id, active, priority);


--
-- Name: idx_rfq_status; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX idx_rfq_status ON pur.rfq_hdr USING btree (status);


--
-- Name: ix_grn_dtl_po_detail; Type: INDEX; Schema: pur; Owner: -
--

CREATE INDEX ix_grn_dtl_po_detail ON pur.grn_dtl USING btree (po_detail_id);


--
-- Name: idx_quotation_customer; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_quotation_customer ON sal.sales_quotation_hdr USING btree (customer_id);


--
-- Name: idx_quotation_dtl_header; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_quotation_dtl_header ON sal.sales_quotation_dtl USING btree (header_id);


--
-- Name: idx_quotation_status; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_quotation_status ON sal.sales_quotation_hdr USING btree (status);


--
-- Name: idx_sal_do_warehouse_status_do_gi; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_sal_do_warehouse_status_do_gi ON sal.delivery_order_hdr USING btree (company_id, warehouse_id, status);


--
-- Name: idx_so_dtl; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_so_dtl ON sal.sales_order_dtl USING btree (header_id);


--
-- Name: idx_so_hdr; Type: INDEX; Schema: sal; Owner: -
--

CREATE INDEX idx_so_hdr ON sal.sales_order_hdr USING btree (doc_date, status);


--
-- Name: idx_outbox_pending; Type: INDEX; Schema: sys; Owner: -
--

CREATE INDEX idx_outbox_pending ON sys.outbox_event USING btree (next_attempt_at) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'FAILED'::character varying])::text[]));


--
-- Name: audit_log_2026_11_pkey; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.audit_log_pkey ATTACH PARTITION core.audit_log_2026_11_pkey;


--
-- Name: audit_log_2026_11_schema_name_table_name_record_id_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_record ATTACH PARTITION core.audit_log_2026_11_schema_name_table_name_record_id_idx;


--
-- Name: audit_log_2026_11_user_id_created_at_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_user ATTACH PARTITION core.audit_log_2026_11_user_id_created_at_idx;


--
-- Name: audit_log_2026_12_pkey; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.audit_log_pkey ATTACH PARTITION core.audit_log_2026_12_pkey;


--
-- Name: audit_log_2026_12_schema_name_table_name_record_id_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_record ATTACH PARTITION core.audit_log_2026_12_schema_name_table_name_record_id_idx;


--
-- Name: audit_log_2026_12_user_id_created_at_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_user ATTACH PARTITION core.audit_log_2026_12_user_id_created_at_idx;


--
-- Name: audit_log_2027_01_pkey; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.audit_log_pkey ATTACH PARTITION core.audit_log_2027_01_pkey;


--
-- Name: audit_log_2027_01_schema_name_table_name_record_id_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_record ATTACH PARTITION core.audit_log_2027_01_schema_name_table_name_record_id_idx;


--
-- Name: audit_log_2027_01_user_id_created_at_idx; Type: INDEX ATTACH; Schema: core; Owner: -
--

ALTER INDEX core.idx_audit_user ATTACH PARTITION core.audit_log_2027_01_user_id_created_at_idx;


--
-- Name: app_user trg_app_user_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_app_user_updated BEFORE UPDATE ON core.app_user FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: audit_log_2026_11 trg_audit_log_2026_11_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_audit_log_2026_11_updated BEFORE UPDATE ON core.audit_log_2026_11 FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: audit_log_2026_12 trg_audit_log_2026_12_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_audit_log_2026_12_updated BEFORE UPDATE ON core.audit_log_2026_12 FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: audit_log_2027_01 trg_audit_log_2027_01_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_audit_log_2027_01_updated BEFORE UPDATE ON core.audit_log_2027_01 FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: company trg_company_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_company_updated BEFORE UPDATE ON core.company FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: role trg_role_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_role_updated BEFORE UPDATE ON core.role FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: tenant trg_tenant_updated; Type: TRIGGER; Schema: core; Owner: -
--

CREATE TRIGGER trg_tenant_updated BEFORE UPDATE ON core.tenant FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: chart_of_account trg_chart_of_account_updated; Type: TRIGGER; Schema: fin; Owner: -
--

CREATE TRIGGER trg_chart_of_account_updated BEFORE UPDATE ON fin.chart_of_account FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: currency trg_currency_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_currency_updated BEFORE UPDATE ON md.currency FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: customer trg_customer_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_customer_updated BEFORE UPDATE ON md.customer FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: item_category trg_item_category_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_item_category_updated BEFORE UPDATE ON md.item_category FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: item trg_item_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_item_updated BEFORE UPDATE ON md.item FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: supplier trg_supplier_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_supplier_updated BEFORE UPDATE ON md.supplier FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: tax trg_tax_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_tax_updated BEFORE UPDATE ON md.tax FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: uom_conversion trg_uom_conversion_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_uom_conversion_updated BEFORE UPDATE ON md.uom_conversion FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: uom trg_uom_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_uom_updated BEFORE UPDATE ON md.uom FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: warehouse trg_warehouse_updated; Type: TRIGGER; Schema: md; Owner: -
--

CREATE TRIGGER trg_warehouse_updated BEFORE UPDATE ON md.warehouse FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: app_config trg_app_config_updated; Type: TRIGGER; Schema: sys; Owner: -
--

CREATE TRIGGER trg_app_config_updated BEFORE UPDATE ON sys.app_config FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: doc_number_counter trg_doc_number_counter_updated; Type: TRIGGER; Schema: sys; Owner: -
--

CREATE TRIGGER trg_doc_number_counter_updated BEFORE UPDATE ON sys.doc_number_counter FOR EACH ROW EXECUTE FUNCTION core.set_updated_at();


--
-- Name: app_user app_user_default_company_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.app_user
    ADD CONSTRAINT app_user_default_company_id_fkey FOREIGN KEY (default_company_id) REFERENCES core.company(id);


--
-- Name: app_user app_user_employee_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.app_user
    ADD CONSTRAINT app_user_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES hr.employee(id);


--
-- Name: app_user app_user_tenant_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.app_user
    ADD CONSTRAINT app_user_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES core.tenant(id);


--
-- Name: company company_parent_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.company
    ADD CONSTRAINT company_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES core.company(id);


--
-- Name: company company_tenant_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.company
    ADD CONSTRAINT company_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES core.tenant(id);


--
-- Name: menu menu_parent_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.menu
    ADD CONSTRAINT menu_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES core.menu(id) ON DELETE CASCADE;


--
-- Name: refresh_token refresh_token_user_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.refresh_token
    ADD CONSTRAINT refresh_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES core.app_user(id) ON DELETE CASCADE;


--
-- Name: role_permission role_permission_permission_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role_permission
    ADD CONSTRAINT role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES core.permission(id) ON DELETE CASCADE;


--
-- Name: role_permission role_permission_role_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role_permission
    ADD CONSTRAINT role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES core.role(id) ON DELETE CASCADE;


--
-- Name: role role_tenant_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.role
    ADD CONSTRAINT role_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES core.tenant(id);


--
-- Name: user_company user_company_company_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_company
    ADD CONSTRAINT user_company_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id) ON DELETE CASCADE;


--
-- Name: user_company user_company_user_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_company
    ADD CONSTRAINT user_company_user_id_fkey FOREIGN KEY (user_id) REFERENCES core.app_user(id) ON DELETE CASCADE;


--
-- Name: user_role user_role_role_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_role
    ADD CONSTRAINT user_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES core.role(id) ON DELETE CASCADE;


--
-- Name: user_role user_role_user_id_fkey; Type: FK CONSTRAINT; Schema: core; Owner: -
--

ALTER TABLE ONLY core.user_role
    ADD CONSTRAINT user_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES core.app_user(id) ON DELETE CASCADE;


--
-- Name: ap_payment ap_payment_bank_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ap_payment
    ADD CONSTRAINT ap_payment_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES fin.bank_account(id);


--
-- Name: ar_collection ar_collection_bank_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ar_collection
    ADD CONSTRAINT ar_collection_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES fin.bank_account(id);


--
-- Name: asset_depreciation_log asset_depreciation_log_asset_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.asset_depreciation_log
    ADD CONSTRAINT asset_depreciation_log_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES fin.asset_master(id);


--
-- Name: cash_bank_trx cash_bank_trx_bank_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cash_bank_trx
    ADD CONSTRAINT cash_bank_trx_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES fin.bank_account(id);


--
-- Name: chart_of_account chart_of_account_company_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.chart_of_account
    ADD CONSTRAINT chart_of_account_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: chart_of_account chart_of_account_parent_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.chart_of_account
    ADD CONSTRAINT chart_of_account_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES fin.chart_of_account(id);


--
-- Name: cost_center cost_center_parent_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.cost_center
    ADD CONSTRAINT cost_center_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES fin.cost_center(id);


--
-- Name: fiscal_period_audit fiscal_period_audit_fiscal_period_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.fiscal_period_audit
    ADD CONSTRAINT fiscal_period_audit_fiscal_period_id_fkey FOREIGN KEY (fiscal_period_id) REFERENCES fin.fiscal_period(id);


--
-- Name: ap_payment_dtl fk_ap_pay_dtl_payment; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ap_payment_dtl
    ADD CONSTRAINT fk_ap_pay_dtl_payment FOREIGN KEY (payment_id) REFERENCES fin.cash_bank_trx(id) ON DELETE CASCADE;


--
-- Name: ar_receipt_dtl fk_ar_receipt_dtl_receipt; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.ar_receipt_dtl
    ADD CONSTRAINT fk_ar_receipt_dtl_receipt FOREIGN KEY (receipt_id) REFERENCES fin.cash_bank_trx(id) ON DELETE CASCADE;


--
-- Name: purchase_order fk_po_pr; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.purchase_order
    ADD CONSTRAINT fk_po_pr FOREIGN KEY (pr_id) REFERENCES fin.purchase_requisition(id);


--
-- Name: journal_dtl journal_dtl_account_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.journal_dtl
    ADD CONSTRAINT journal_dtl_account_id_fkey FOREIGN KEY (account_id) REFERENCES fin.chart_of_account(id);


--
-- Name: journal_dtl journal_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.journal_dtl
    ADD CONSTRAINT journal_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES fin.journal_hdr(id) ON DELETE CASCADE;


--
-- Name: payment_dtl payment_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.payment_dtl
    ADD CONSTRAINT payment_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES fin.payment_hdr(id) ON DELETE CASCADE;


--
-- Name: profit_center profit_center_parent_id_fkey; Type: FK CONSTRAINT; Schema: fin; Owner: -
--

ALTER TABLE ONLY fin.profit_center
    ADD CONSTRAINT profit_center_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES fin.profit_center(id);


--
-- Name: attendance attendance_employee_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.attendance
    ADD CONSTRAINT attendance_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES hr.employee(id);


--
-- Name: cash_advance cash_advance_bank_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.cash_advance
    ADD CONSTRAINT cash_advance_bank_id_fkey FOREIGN KEY (bank_id) REFERENCES fin.bank_account(id);


--
-- Name: cash_advance cash_advance_employee_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.cash_advance
    ADD CONSTRAINT cash_advance_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES hr.employee(id);


--
-- Name: cash_advance_expense cash_advance_expense_cash_advance_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.cash_advance_expense
    ADD CONSTRAINT cash_advance_expense_cash_advance_id_fkey FOREIGN KEY (cash_advance_id) REFERENCES hr.cash_advance(id) ON DELETE CASCADE;


--
-- Name: department department_cost_center_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.department
    ADD CONSTRAINT department_cost_center_id_fkey FOREIGN KEY (cost_center_id) REFERENCES fin.cost_center(id);


--
-- Name: department department_parent_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.department
    ADD CONSTRAINT department_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES hr.department(id);


--
-- Name: employee_salary employee_salary_employee_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.employee_salary
    ADD CONSTRAINT employee_salary_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES hr.employee(id);


--
-- Name: payroll_dtl payroll_dtl_employee_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.payroll_dtl
    ADD CONSTRAINT payroll_dtl_employee_id_fkey FOREIGN KEY (employee_id) REFERENCES hr.employee(id);


--
-- Name: payroll_dtl payroll_dtl_payroll_run_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr.payroll_dtl
    ADD CONSTRAINT payroll_dtl_payroll_run_id_fkey FOREIGN KEY (payroll_run_id) REFERENCES hr.payroll_run(id) ON DELETE CASCADE;


--
-- Name: position position_department_id_fkey; Type: FK CONSTRAINT; Schema: hr; Owner: -
--

ALTER TABLE ONLY hr."position"
    ADD CONSTRAINT position_department_id_fkey FOREIGN KEY (department_id) REFERENCES hr.department(id);


--
-- Name: transaction_detail fk_inv_trx_reversal_detail_do_gi; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT fk_inv_trx_reversal_detail_do_gi FOREIGN KEY (reversal_of_detail_id) REFERENCES inv.transaction_detail(id);


--
-- Name: transaction_header fk_inv_trx_reversal_header_do_gi; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT fk_inv_trx_reversal_header_do_gi FOREIGN KEY (reversal_of_header_id) REFERENCES inv.transaction_header(id);


--
-- Name: transaction_detail fk_inv_trx_source_do_detail_do_gi; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT fk_inv_trx_source_do_detail_do_gi FOREIGN KEY (source_do_detail_id) REFERENCES sal.delivery_order_dtl(id);


--
-- Name: transaction_header fk_inv_trx_source_do_do_gi; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT fk_inv_trx_source_do_do_gi FOREIGN KEY (source_do_id) REFERENCES sal.delivery_order_hdr(id);


--
-- Name: inventory_period_control inventory_period_control_company_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.inventory_period_control
    ADD CONSTRAINT inventory_period_control_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: inventory_period_control inventory_period_control_tenant_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.inventory_period_control
    ADD CONSTRAINT inventory_period_control_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES core.tenant(id);


--
-- Name: stock_opname_dtl stock_opname_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_opname_dtl
    ADD CONSTRAINT stock_opname_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES inv.stock_opname_hdr(id) ON DELETE CASCADE;


--
-- Name: stock_transfer_dtl stock_transfer_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.stock_transfer_dtl
    ADD CONSTRAINT stock_transfer_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES inv.stock_transfer_hdr(id) ON DELETE CASCADE;


--
-- Name: transaction_detail transaction_detail_header_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT transaction_detail_header_id_fkey FOREIGN KEY (header_id) REFERENCES inv.transaction_header(id) ON DELETE CASCADE;


--
-- Name: transaction_detail transaction_detail_item_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT transaction_detail_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: transaction_detail transaction_detail_uom_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_detail
    ADD CONSTRAINT transaction_detail_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: transaction_header transaction_header_company_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT transaction_header_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: transaction_header transaction_header_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: inv; Owner: -
--

ALTER TABLE ONLY inv.transaction_header
    ADD CONSTRAINT transaction_header_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: bom_detail bom_detail_bom_header_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_detail
    ADD CONSTRAINT bom_detail_bom_header_id_fkey FOREIGN KEY (bom_header_id) REFERENCES md.bom_header(id) ON DELETE CASCADE;


--
-- Name: bom_detail bom_detail_material_item_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_detail
    ADD CONSTRAINT bom_detail_material_item_id_fkey FOREIGN KEY (material_item_id) REFERENCES md.item(id);


--
-- Name: bom_detail bom_detail_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_detail
    ADD CONSTRAINT bom_detail_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: bom_header bom_header_product_item_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.bom_header
    ADD CONSTRAINT bom_header_product_item_id_fkey FOREIGN KEY (product_item_id) REFERENCES md.item(id);


--
-- Name: customer customer_company_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.customer
    ADD CONSTRAINT customer_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: customer customer_currency_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.customer
    ADD CONSTRAINT customer_currency_fkey FOREIGN KEY (currency) REFERENCES md.currency(code);


--
-- Name: customer customer_default_tax_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.customer
    ADD CONSTRAINT customer_default_tax_id_fkey FOREIGN KEY (default_tax_id) REFERENCES md.tax(id);


--
-- Name: exchange_rate exchange_rate_from_currency_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.exchange_rate
    ADD CONSTRAINT exchange_rate_from_currency_fkey FOREIGN KEY (from_currency) REFERENCES md.currency(code);


--
-- Name: exchange_rate exchange_rate_to_currency_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.exchange_rate
    ADD CONSTRAINT exchange_rate_to_currency_fkey FOREIGN KEY (to_currency) REFERENCES md.currency(code);


--
-- Name: item_price fk_item_price_uom; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT fk_item_price_uom FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: item item_base_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_base_uom_id_fkey FOREIGN KEY (base_uom_id) REFERENCES md.uom(id);


--
-- Name: item_batch item_batch_item_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_batch
    ADD CONSTRAINT item_batch_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: item_batch item_batch_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_batch
    ADD CONSTRAINT item_batch_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: item item_category_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_category_id_fkey FOREIGN KEY (category_id) REFERENCES md.item_category(id);


--
-- Name: item_category item_category_parent_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_category
    ADD CONSTRAINT item_category_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES md.item_category(id);


--
-- Name: item item_company_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: item item_djp_goods_code_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_djp_goods_code_fkey FOREIGN KEY (djp_goods_code) REFERENCES md.djp_goods_code(code);


--
-- Name: item_price item_price_company_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT item_price_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: item_price item_price_currency_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT item_price_currency_fkey FOREIGN KEY (currency) REFERENCES md.currency(code);


--
-- Name: item_price item_price_item_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_price
    ADD CONSTRAINT item_price_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: item_serial item_serial_item_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item_serial
    ADD CONSTRAINT item_serial_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: item item_uom_set_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.item
    ADD CONSTRAINT item_uom_set_id_fkey FOREIGN KEY (uom_set_id) REFERENCES md.uom_set(id);


--
-- Name: supplier supplier_company_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.supplier
    ADD CONSTRAINT supplier_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: supplier supplier_currency_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.supplier
    ADD CONSTRAINT supplier_currency_fkey FOREIGN KEY (currency) REFERENCES md.currency(code);


--
-- Name: supplier supplier_default_tax_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.supplier
    ADD CONSTRAINT supplier_default_tax_id_fkey FOREIGN KEY (default_tax_id) REFERENCES md.tax(id);


--
-- Name: uom_conversion uom_conversion_from_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_conversion
    ADD CONSTRAINT uom_conversion_from_uom_id_fkey FOREIGN KEY (from_uom_id) REFERENCES md.uom(id);


--
-- Name: uom_conversion uom_conversion_to_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_conversion
    ADD CONSTRAINT uom_conversion_to_uom_id_fkey FOREIGN KEY (to_uom_id) REFERENCES md.uom(id);


--
-- Name: uom_set_dtl uom_set_dtl_parent_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl
    ADD CONSTRAINT uom_set_dtl_parent_uom_id_fkey FOREIGN KEY (parent_uom_id) REFERENCES md.uom(id);


--
-- Name: uom_set_dtl uom_set_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl
    ADD CONSTRAINT uom_set_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: uom_set_dtl uom_set_dtl_uom_set_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.uom_set_dtl
    ADD CONSTRAINT uom_set_dtl_uom_set_id_fkey FOREIGN KEY (uom_set_id) REFERENCES md.uom_set(id) ON DELETE CASCADE;


--
-- Name: warehouse_bin warehouse_bin_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse_bin
    ADD CONSTRAINT warehouse_bin_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: warehouse warehouse_company_id_fkey; Type: FK CONSTRAINT; Schema: md; Owner: -
--

ALTER TABLE ONLY md.warehouse
    ADD CONSTRAINT warehouse_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: grn_dtl grn_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_dtl
    ADD CONSTRAINT grn_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.grn_hdr(id) ON DELETE CASCADE;


--
-- Name: grn_dtl grn_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_dtl
    ADD CONSTRAINT grn_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: grn_dtl grn_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_dtl
    ADD CONSTRAINT grn_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: grn_hdr grn_hdr_company_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_hdr
    ADD CONSTRAINT grn_hdr_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: grn_hdr grn_hdr_po_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.grn_hdr
    ADD CONSTRAINT grn_hdr_po_id_fkey FOREIGN KEY (po_id) REFERENCES pur.purchase_order_hdr(id);


--
-- Name: purchase_invoice_dtl purchase_invoice_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_dtl
    ADD CONSTRAINT purchase_invoice_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.purchase_invoice_hdr(id) ON DELETE CASCADE;


--
-- Name: purchase_invoice_dtl purchase_invoice_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_dtl
    ADD CONSTRAINT purchase_invoice_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: purchase_invoice_hdr purchase_invoice_hdr_company_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_hdr
    ADD CONSTRAINT purchase_invoice_hdr_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: purchase_invoice_hdr purchase_invoice_hdr_grn_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_hdr
    ADD CONSTRAINT purchase_invoice_hdr_grn_id_fkey FOREIGN KEY (grn_id) REFERENCES pur.grn_hdr(id);


--
-- Name: purchase_invoice_hdr purchase_invoice_hdr_supplier_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_invoice_hdr
    ADD CONSTRAINT purchase_invoice_hdr_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES md.supplier(id);


--
-- Name: purchase_order_dtl purchase_order_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_dtl
    ADD CONSTRAINT purchase_order_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.purchase_order_hdr(id) ON DELETE CASCADE;


--
-- Name: purchase_order_dtl purchase_order_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_dtl
    ADD CONSTRAINT purchase_order_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: purchase_order_dtl purchase_order_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_dtl
    ADD CONSTRAINT purchase_order_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: purchase_order_hdr purchase_order_hdr_company_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: purchase_order_hdr purchase_order_hdr_pr_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_pr_id_fkey FOREIGN KEY (pr_id) REFERENCES pur.purchase_request_hdr(id);


--
-- Name: purchase_order_hdr purchase_order_hdr_supplier_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_order_hdr
    ADD CONSTRAINT purchase_order_hdr_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES md.supplier(id);


--
-- Name: purchase_policy purchase_policy_company_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_policy
    ADD CONSTRAINT purchase_policy_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: purchase_request_dtl purchase_request_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_dtl
    ADD CONSTRAINT purchase_request_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.purchase_request_hdr(id) ON DELETE CASCADE;


--
-- Name: purchase_request_dtl purchase_request_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_dtl
    ADD CONSTRAINT purchase_request_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: purchase_request_dtl purchase_request_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_dtl
    ADD CONSTRAINT purchase_request_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: purchase_request_hdr purchase_request_hdr_company_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_hdr
    ADD CONSTRAINT purchase_request_hdr_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: purchase_request_hdr purchase_request_hdr_requisitioner_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_request_hdr
    ADD CONSTRAINT purchase_request_hdr_requisitioner_id_fkey FOREIGN KEY (requisitioner_id) REFERENCES hr.employee(id);


--
-- Name: purchase_return_dtl purchase_return_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_dtl
    ADD CONSTRAINT purchase_return_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.purchase_return_hdr(id) ON DELETE CASCADE;


--
-- Name: purchase_return_dtl purchase_return_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_dtl
    ADD CONSTRAINT purchase_return_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: purchase_return_dtl purchase_return_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_dtl
    ADD CONSTRAINT purchase_return_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: purchase_return_hdr purchase_return_hdr_purchase_invoice_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_hdr
    ADD CONSTRAINT purchase_return_hdr_purchase_invoice_id_fkey FOREIGN KEY (purchase_invoice_id) REFERENCES pur.purchase_invoice_hdr(id);


--
-- Name: purchase_return_hdr purchase_return_hdr_supplier_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_hdr
    ADD CONSTRAINT purchase_return_hdr_supplier_id_fkey FOREIGN KEY (supplier_id) REFERENCES md.supplier(id);


--
-- Name: purchase_return_hdr purchase_return_hdr_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.purchase_return_hdr
    ADD CONSTRAINT purchase_return_hdr_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: rfq_dtl rfq_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_dtl
    ADD CONSTRAINT rfq_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES pur.rfq_hdr(id) ON DELETE CASCADE;


--
-- Name: rfq_response rfq_response_rfq_dtl_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_response
    ADD CONSTRAINT rfq_response_rfq_dtl_id_fkey FOREIGN KEY (rfq_dtl_id) REFERENCES pur.rfq_dtl(id) ON DELETE CASCADE;


--
-- Name: rfq_response rfq_response_rfq_supplier_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_response
    ADD CONSTRAINT rfq_response_rfq_supplier_id_fkey FOREIGN KEY (rfq_supplier_id) REFERENCES pur.rfq_supplier(id) ON DELETE CASCADE;


--
-- Name: rfq_supplier rfq_supplier_rfq_id_fkey; Type: FK CONSTRAINT; Schema: pur; Owner: -
--

ALTER TABLE ONLY pur.rfq_supplier
    ADD CONSTRAINT rfq_supplier_rfq_id_fkey FOREIGN KEY (rfq_id) REFERENCES pur.rfq_hdr(id) ON DELETE CASCADE;


--
-- Name: delivery_order_dtl delivery_order_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_dtl
    ADD CONSTRAINT delivery_order_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES sal.delivery_order_hdr(id) ON DELETE CASCADE;


--
-- Name: delivery_order_hdr delivery_order_hdr_so_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_hdr
    ADD CONSTRAINT delivery_order_hdr_so_id_fkey FOREIGN KEY (so_id) REFERENCES sal.sales_order_hdr(id);


--
-- Name: delivery_order_hdr fk_sal_do_warehouse_do_gi; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.delivery_order_hdr
    ADD CONSTRAINT fk_sal_do_warehouse_do_gi FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: sales_invoice_dtl sales_invoice_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_dtl
    ADD CONSTRAINT sales_invoice_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES sal.sales_invoice_hdr(id) ON DELETE CASCADE;


--
-- Name: sales_invoice_hdr sales_invoice_hdr_customer_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_hdr
    ADD CONSTRAINT sales_invoice_hdr_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES md.customer(id);


--
-- Name: sales_invoice_hdr sales_invoice_hdr_do_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_hdr
    ADD CONSTRAINT sales_invoice_hdr_do_id_fkey FOREIGN KEY (do_id) REFERENCES sal.delivery_order_hdr(id);


--
-- Name: sales_invoice_hdr sales_invoice_hdr_salesperson_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_invoice_hdr
    ADD CONSTRAINT sales_invoice_hdr_salesperson_id_fkey FOREIGN KEY (salesperson_id) REFERENCES md.salesperson(id);


--
-- Name: sales_order_dtl sales_order_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_dtl
    ADD CONSTRAINT sales_order_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES sal.sales_order_hdr(id) ON DELETE CASCADE;


--
-- Name: sales_order_dtl sales_order_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_dtl
    ADD CONSTRAINT sales_order_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: sales_order_dtl sales_order_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_dtl
    ADD CONSTRAINT sales_order_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: sales_order_hdr sales_order_hdr_company_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_hdr
    ADD CONSTRAINT sales_order_hdr_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- Name: sales_order_hdr sales_order_hdr_customer_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_hdr
    ADD CONSTRAINT sales_order_hdr_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES md.customer(id);


--
-- Name: sales_order_hdr sales_order_hdr_salesperson_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_order_hdr
    ADD CONSTRAINT sales_order_hdr_salesperson_id_fkey FOREIGN KEY (salesperson_id) REFERENCES md.salesperson(id);


--
-- Name: sales_quotation_dtl sales_quotation_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_quotation_dtl
    ADD CONSTRAINT sales_quotation_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES sal.sales_quotation_hdr(id) ON DELETE CASCADE;


--
-- Name: sales_return_dtl sales_return_dtl_header_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_dtl
    ADD CONSTRAINT sales_return_dtl_header_id_fkey FOREIGN KEY (header_id) REFERENCES sal.sales_return_hdr(id) ON DELETE CASCADE;


--
-- Name: sales_return_dtl sales_return_dtl_item_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_dtl
    ADD CONSTRAINT sales_return_dtl_item_id_fkey FOREIGN KEY (item_id) REFERENCES md.item(id);


--
-- Name: sales_return_dtl sales_return_dtl_uom_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_dtl
    ADD CONSTRAINT sales_return_dtl_uom_id_fkey FOREIGN KEY (uom_id) REFERENCES md.uom(id);


--
-- Name: sales_return_hdr sales_return_hdr_customer_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_hdr
    ADD CONSTRAINT sales_return_hdr_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES md.customer(id);


--
-- Name: sales_return_hdr sales_return_hdr_sales_invoice_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_hdr
    ADD CONSTRAINT sales_return_hdr_sales_invoice_id_fkey FOREIGN KEY (sales_invoice_id) REFERENCES sal.sales_invoice_hdr(id);


--
-- Name: sales_return_hdr sales_return_hdr_warehouse_id_fkey; Type: FK CONSTRAINT; Schema: sal; Owner: -
--

ALTER TABLE ONLY sal.sales_return_hdr
    ADD CONSTRAINT sales_return_hdr_warehouse_id_fkey FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);


--
-- Name: doc_number_counter doc_number_counter_company_id_fkey; Type: FK CONSTRAINT; Schema: sys; Owner: -
--

ALTER TABLE ONLY sys.doc_number_counter
    ADD CONSTRAINT doc_number_counter_company_id_fkey FOREIGN KEY (company_id) REFERENCES core.company(id);


--
-- PostgreSQL database dump complete
--

\unrestrict RPjzbUxZZJuBgfEHBfcoVOeFTOaUlNeYWZQzVMldv15KSXWacbEPN6dxPexarVY

