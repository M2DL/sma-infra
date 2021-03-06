package com.m2dl.sma.infrastructure.annuaire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import com.m2dl.sma.infrastructure.agent.Agent;
import com.m2dl.sma.infrastructure.agent.ReferenceAgent;
import com.m2dl.sma.infrastructure.communication.AgentNotFoundException;
import com.m2dl.sma.infrastructure.communication.MessageAgent;

public class AnnuaireImpl implements Annuaire {

    private List<AnnuaireListener> annuaireListeners;
    private ConcurrentMap<ReferenceAgent, Agent> agents;
    private ConcurrentMap<ReferenceAgent, ConcurrentLinkedQueue<MessageAgent>> agentsMessagesQueues;
    private ConcurrentMap<ReferenceAgent, ReadWriteLock> agentsLocks;

    public AnnuaireImpl() {
        annuaireListeners = Collections.synchronizedList(new ArrayList<>());
        agents = new ConcurrentHashMap<>();
        agentsMessagesQueues = new ConcurrentHashMap<>();
        agentsLocks = new ConcurrentHashMap<>();
    }

    @Override
    public void ajouterAgent(ReferenceAgent referenceAgent, Agent agent) {
        agentsLocks.put(referenceAgent, new ReentrantReadWriteLock());
        lockAgentEcriture(referenceAgent);
        agents.put(referenceAgent, agent);
        agentsMessagesQueues.put(referenceAgent, new ConcurrentLinkedQueue<>());
        unlockAgentEcriture(referenceAgent);
        annuaireListeners.forEach(annuaireListener -> annuaireListener.agentAjoute(referenceAgent));
    }

    @Override
    public void retirerAgent(ReferenceAgent referenceAgent) {
        lockAgentEcriture(referenceAgent);
        agents.remove(referenceAgent);
        agentsMessagesQueues.remove(referenceAgent);
        unlockAgentEcriture(referenceAgent);
        annuaireListeners.forEach(annuaireListener -> annuaireListener.agentRetire(referenceAgent));
    }

    @Override
    public void envoyerMessage(ReferenceAgent expediteur, ReferenceAgent destinataire,
            MessageAgent messageAgent) throws AgentNotFoundException {
        lockAgentLecture(destinataire);
        if (!agentsMessagesQueues.containsKey(destinataire)) {
            throw new AgentNotFoundException();
        }
        agentsMessagesQueues.get(destinataire).add(messageAgent);
        unlockAgentLecture(destinataire);
    }

    @Override
    public void diffuserMessage(ReferenceAgent expediteur, MessageAgent messageAgent) {
        agentsMessagesQueues.keySet().forEach(this::lockAgentLecture);
        agentsMessagesQueues.values().forEach(messageAgents -> messageAgents.add(messageAgent));
        agentsMessagesQueues.keySet().forEach(this::unlockAgentLecture);
    }

    @Override
    public Optional<MessageAgent> recevoirMessage(ReferenceAgent destinataire) {
        lockAgentLecture(destinataire);
        Optional<MessageAgent> message = Optional.ofNullable(
                agentsMessagesQueues.get(destinataire)).map(ConcurrentLinkedQueue::poll);
        unlockAgentLecture(destinataire);
        return message;
    }

    @Override
    public void ajouterListener(AnnuaireListener annuaireListener) {
        annuaireListeners.add(annuaireListener);
    }

    @Override
    public void retirerListener(AnnuaireListener annuaireListener) {
        annuaireListeners.remove(annuaireListener);
    }

    private void lockAgentEcriture(ReferenceAgent referenceAgent) {
        executeIfPresent(agentsLocks.get(referenceAgent),
                readWriteLock -> readWriteLock.writeLock().lock());
    }

    private void lockAgentLecture(ReferenceAgent referenceAgent) {
        executeIfPresent(agentsLocks.get(referenceAgent),
                readWriteLock -> readWriteLock.readLock().lock());
    }

    private void unlockAgentEcriture(ReferenceAgent referenceAgent) {
        executeIfPresent(agentsLocks.get(referenceAgent),
                readWriteLock -> readWriteLock.writeLock().unlock());
    }

    private void unlockAgentLecture(ReferenceAgent referenceAgent) {
        executeIfPresent(agentsLocks.get(referenceAgent),
                readWriteLock -> readWriteLock.readLock().unlock());
    }

    private <T> void executeIfPresent(T object, Consumer<T> objectConsumer) {
        if (object != null) {
            objectConsumer.accept(object);
        }
    }
}
