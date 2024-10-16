package br.insper.aposta.aposta;

import br.insper.aposta.partida.PartidaNaoEncontradaException;
import br.insper.aposta.partida.PartidaNaoRealizadaException;
import br.insper.aposta.partida.PartidaService;
import br.insper.loja.partida.dto.RetornarPartidaDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApostaService {

    @Autowired
    private ApostaRepository apostaRepository;

    @Autowired
    private PartidaService partidaService;

    public Aposta salvar(Aposta aposta) {
        aposta.setId(UUID.randomUUID().toString());

        ResponseEntity<RetornarPartidaDTO> partida = partidaService.getPartida(aposta.getIdPartida());

        if (partida.getStatusCode().is2xxSuccessful())  {
            aposta.setStatus("REALIZADA");
            aposta.setDataAposta(LocalDateTime.now());

            return apostaRepository.save(aposta);
        } else {
            throw new PartidaNaoEncontradaException("Partida não encontrada");
        }

    }

    public List<Aposta> listar() {
        return apostaRepository.findAll();
    }


    @KafkaListener(topics = "partidas")
    public void atualizaApostas(RetornarPartidaDTO dto) {
        // Busca todas as apostas no banco de dados
        List<Aposta> apostas = apostaRepository.findByPartida(dto.getId());

        for (Aposta aposta : apostas) {
            try {
                // Obtém a partida associada à aposta
                ResponseEntity<RetornarPartidaDTO> partida = partidaService.getPartida(aposta.getIdPartida());

                if (partida.getStatusCode().is2xxSuccessful()) {
                    RetornarPartidaDTO partidaDTO = partida.getBody();

                    // Verifica se a partida já foi realizada
                    if (partidaDTO.getStatus().equals("REALIZADA")) {
                        // Atualiza o status da aposta conforme o resultado da partida
                        if (aposta.getResultado().equals("EMPATE") && partidaDTO.isEmpate()) {
                            aposta.setStatus("GANHOU");
                        } else if (aposta.getResultado().equals("VITORIA_MANDANTE") && partidaDTO.isVitoriaMandante()) {
                            aposta.setStatus("GANHOU");
                        } else if (aposta.getResultado().equals("VITORIA_VISITANTE") && partidaDTO.isVitoriaVisitante()) {
                            aposta.setStatus("GANHOU");
                        } else {
                            aposta.setStatus("PERDEU");
                        }

                        // Salva a aposta atualizada no banco de dados
                        apostaRepository.save(aposta);
                    } else {
                        throw new PartidaNaoRealizadaException("Partida não realizada");
                    }
                } else {
                    throw new PartidaNaoEncontradaException("Partida não encontrada");
                }
            } catch (Exception e) {
                // Trate a exceção de forma apropriada, log ou outras ações
                System.err.println("Erro ao atualizar aposta: " + e.getMessage());
            }
        }
    }

    public Aposta getAposta(String idAposta) {

        Optional<Aposta> op = apostaRepository.findById(idAposta);

        if (!op.isPresent()) {
            throw new ApostaNaoEncontradaException("Aposta não encontrada");
        }

        Aposta aposta = op.get();

        return aposta;

    }
}
